package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IPaymentService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付控制器
 */
@Slf4j
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final IPaymentService paymentService;
    private final IVoucherOrderService voucherOrderService;

    @PostMapping("/create")
    public Result createPayment(@RequestParam("orderId") Long orderId,
                                @RequestParam(value = "payType", defaultValue = "1") Integer payType) {
        Long userId = UserHolder.getUser().getId();

        VoucherOrder order = voucherOrderService.getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        if (!order.getUserId().equals(userId)) {
            return Result.fail("订单不属于当前用户");
        }

        if (!VoucherOrder.STATUS_UNPAID.equals(order.getStatus())) {
            return Result.fail("订单状态不正确");
        }
        log.info("创建支付订单成功");
        return paymentService.createPayment(orderId, payType);
    }

    @PostMapping("/simulate/{id}")
    public Result simulatePayment(@PathVariable("id") Long orderId) {
        Long userId = UserHolder.getUser().getId();

        VoucherOrder order = voucherOrderService.getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        if (!order.getUserId().equals(userId)) {
            return Result.fail("订单不属于当前用户");
        }

        if (!VoucherOrder.STATUS_UNPAID.equals(order.getStatus())) {
            return Result.fail("订单状态不正确");
        }

        return paymentService.simulatePaymentCallback(orderId);
    }

    @PostMapping("/wechat/{id}")
    public Result wechatPay(@PathVariable("id") Long orderId) {
        return createPaymentAndReturnUrl(orderId, VoucherOrder.PAY_TYPE_WECHAT);
    }

    @PostMapping("/alipay/{id}")
    public Result alipay(@PathVariable("id") Long orderId) {
        return createPaymentAndReturnUrl(orderId, VoucherOrder.PAY_TYPE_ALIPAY);
    }

    private Result createPaymentAndReturnUrl(Long orderId, Integer payType) {
        Long userId = UserHolder.getUser().getId();

        VoucherOrder order = voucherOrderService.getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        if (!order.getUserId().equals(userId)) {
            return Result.fail("订单不属于当前用户");
        }

        if (!VoucherOrder.STATUS_UNPAID.equals(order.getStatus())) {
            return Result.fail("订单状态不正确");
        }

        Result paymentResult = paymentService.createPayment(orderId, payType);
        if (!paymentResult.getSuccess()) {
            return paymentResult;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("payType", payType);

        if (payType.equals(VoucherOrder.PAY_TYPE_ALIPAY)) {
            result.put("payUrl", "alipay://...?orderId=" + orderId);
        } else if (payType.equals(VoucherOrder.PAY_TYPE_WECHAT)) {
            result.put("payUrl", "weixin://wxpay/bizpayurl?pr=" + orderId);
        }

        return Result.ok(result);
    }

    @PostMapping("/callback/wechat/{id}")
    public String wechatPayCallback(@PathVariable("id") Long orderId) {
        Result result = paymentService.wechatPayCallback(orderId);
        return result.getSuccess() ? "SUCCESS" : "FAIL";
    }

    @PostMapping("/callback/alipay/{id}")
    public String alipayCallback(@PathVariable("id") Long orderId) {
        Result result = paymentService.alipayCallback(orderId);
        return result.getSuccess() ? "success" : "fail";
    }

    @GetMapping("/status/{id}")
    public Result getPaymentStatus(@PathVariable("id") Long orderId) {
        return paymentService.checkPaymentStatus(orderId);
    }
}
