import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider } from 'antd';
import App from './App';
import './index.css';

// Ant Design Theme Configuration
const theme = {
  token: {
    colorPrimary: '#1890ff',
    borderRadius: 6,
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
  },
  components: {
    Layout: {
      headerBg: '#fff',
      bodyBg: '#f5f5f5',
      siderBg: '#001529',
    },
    Menu: {
      darkItemBg: 'transparent',
      darkSubMenuItemBg: 'rgba(255,255,255,.04)',
      itemBorderRadius: 8,
    }
  }
};

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider theme={theme}>
      <App />
    </ConfigProvider>
  </React.StrictMode>
);
