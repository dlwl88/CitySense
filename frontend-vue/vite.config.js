const vuePlugin = require('@vitejs/plugin-vue');
const autoImportPlugin = require('unplugin-auto-import/dist/vite.cjs');
const componentsPlugin = require('unplugin-vue-components/dist/vite.cjs');
const { ElementPlusResolver } = require('unplugin-vue-components/resolvers');
const path = require('path');

module.exports = {
  root: __dirname,
  plugins: [
    (vuePlugin.default || vuePlugin)(),
    (autoImportPlugin.default || autoImportPlugin)({
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()],
      dts: false
    }),
    (componentsPlugin.default || componentsPlugin)({
      resolvers: [ElementPlusResolver()],
      dts: false
    })
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  build: {
    outDir: path.resolve(__dirname, '../src/main/resources/static'),
    emptyOutDir: true,
    assetsDir: 'assets'
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8083',
        changeOrigin: true,
        rewrite: (requestPath) => requestPath.replace(/^\/api/, '')
      }
    }
  }
};

