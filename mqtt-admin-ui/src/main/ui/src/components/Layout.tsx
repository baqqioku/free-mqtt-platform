import React, { useState } from 'react';
import { Layout, Menu, Avatar, Dropdown, Typography, Badge, Space, theme, Button } from 'antd';
import {
  DashboardOutlined,
  UserOutlined,
  MessageOutlined,
  SettingOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  BellOutlined,
  WifiOutlined,
  ApiOutlined,
  CloudServerOutlined,
  ClusterOutlined
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { PROJECT_SERVICES } from '../constants/platform';

const { Header, Sider, Content, Footer } = Layout;
const { Text } = Typography;

const AppLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();

  const menuItems: MenuProps['items'] = [
    {
      key: '/',
      icon: <DashboardOutlined />,
      label: 'Dashboard',
    },
    {
      key: '/clients',
      icon: <UserOutlined />,
      label: 'Clients',
    },
    {
      key: '/messages',
      icon: <MessageOutlined />,
      label: 'Messages',
    },
    {
      key: '/brokers',
      icon: <ClusterOutlined />,
      label: 'Brokers',
    },
    {
      key: '/settings',
      icon: <SettingOutlined />,
      label: 'Settings',
    },
  ];

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'profile',
      label: 'Profile Settings',
    },
    {
      key: 'logout',
      label: 'Logout',
      danger: true,
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* Sidebar */}
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={260}
        style={{
          overflow: 'auto',
          height: '100vh',
          position: 'fixed',
          left: 0,
          top: 0,
          bottom: 0,
          zIndex: 100
        }}
        className="sidebar"
      >
        {/* Logo */}
        <div className="logo" style={{
          height: 64,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: collapsed ? '0 16px' : '0 24px'
        }}>
          <CloudServerOutlined style={{ fontSize: 28, color: '#1890ff', marginRight: collapsed ? 0 : 12 }} />
          {!collapsed && (
            <Text strong style={{ fontSize: 18, color: '#fff', whiteSpace: 'nowrap' }}>
              MQTT Platform
            </Text>
          )}
        </div>

        {/* Navigation Menu */}
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ borderRight: 0 }}
        />

        {/* System Status */}
        {!collapsed && (
          <div style={{ 
            position: 'absolute', 
            bottom: 20, 
            left: 16, 
            right: 16,
            padding: 12,
            background: 'rgba(255,255,255,0.08)',
            borderRadius: 8
          }}>
            <div style={{ marginBottom: 8 }}>
              <Badge status="success" text={<Text style={{ color: '#fff', fontSize: 12 }}>Broker Online</Text>} />
            </div>
            <div>
              <WifiOutlined style={{ marginRight: 6, fontSize: 12 }} />
              <Text style={{ color: '#fff', fontSize: 11 }}>
                TCP:{PROJECT_SERVICES.broker.tcpPort} | WS:{PROJECT_SERVICES.broker.websocketPort} | HTTP:{PROJECT_SERVICES.broker.httpPort}
              </Text>
            </div>
          </div>
        )}
      </Sider>

      {/* Main Content Area */}
      <Layout
        style={{
          marginLeft: collapsed ? 80 : 260,
          transition: 'margin-left 0.2s',
          minHeight: '100vh'
        }}
      >
        {/* Header */}
        <Header
          style={{
            padding: '0 24px',
            background: colorBgContainer,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            boxShadow: '0 1px 4px rgba(0,21,41,.08)',
            position: 'sticky',
            top: 0,
            zIndex: 99
          }}
        >
          <Space size={16}>
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
              style={{
                fontSize: 16,
                width: 48,
                height: 48,
              }}
            />
            
            {/* Breadcrumb / Page Title */}
            <Typography.Title level={4} style={{ margin: 0, fontWeight: 500 }}>
              {location.pathname === '/' && 'Dashboard'}
              {location.pathname === '/clients' && 'Client Management'}
              {location.pathname === '/messages' && 'Message History'}
              {location.pathname === '/brokers' && 'Broker Cluster'}
              {location.pathname === '/settings' && 'System Settings'}
            </Typography.Title>
          </Space>

          <Space size={16}>
            {/* Notifications */}
            <Dropdown menu={{ items: [{ key: '1', label: 'No new notifications' }] }} trigger={['click']}>
              <Badge count={0}>
                <Button type="text" shape="circle" icon={<BellOutlined />} />
              </Badge>
            </Dropdown>

            {/* User Profile */}
            <Dropdown menu={{ items: userMenuItems }} trigger={['click']}>
              <Space style={{ cursor: 'pointer', padding: '4px 8px', borderRadius: 8 }} className="user-dropdown">
                <Avatar
                  size="small"
                  icon={<ApiOutlined />}
                  style={{ backgroundColor: '#1890ff' }}
                />
                <Text strong>Admin</Text>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        {/* Page Content */}
        <Content
          style={{
            margin: 24,
            minHeight: 280,
            background: colorBgContainer,
            borderRadius: borderRadiusLG,
            overflow: 'auto'
          }}
        >
          <Outlet />
        </Content>

        {/* Footer */}
        <Footer
          style={{
            textAlign: 'center',
            padding: '12px 24px',
            background: colorBgContainer,
            borderTop: '1px solid #f0f0f0'
          }}
        >
          <Text type="secondary" style={{ fontSize: 13 }}>
            MQTT Admin Platform &copy; {new Date().getFullYear()} - Powered by Spring Boot + React
          </Text>
        </Footer>
      </Layout>

      {/* Global Styles */}
      <style>{`
        .ant-layout-sider {
          box-shadow: 2px 0 6px rgba(0,21,41,.35) !important;
        }
        
        .logo:hover {
          background: rgba(255,255,255,.05) !important;
        }
        
        .user-dropdown:hover {
          background: #f5f5f5;
        }
        
        /* Custom scrollbar */
        .ant-layout-sider::-webkit-scrollbar {
          width: 4px;
        }
        .ant-layout-sider::-webkit-scrollbar-thumb {
          background: rgba(255,255,255,.2);
          border-radius: 4px;
        }
        
        /* Smooth transitions */
        * {
          transition-property: all;
          transition-timing-function: ease-in-out;
        }

        /* Ant Design Theme Overrides */
        .ant-table-thead > tr > th {
          font-weight: 600 !important;
          background: #fafafa !important;
        }

        .ant-card {
          border-radius: 8px !important;
          box-shadow: 0 1px 2px rgba(0,0,0,0.03), 0 1px 6px rgba(0,0,0,0.02) !important;
        }

        .ant-btn-primary {
          border-radius: 6px !important;
        }

        /* Animation */
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(10px); }
          to { opacity: 1; transform: translateY(0); }
        }
        
        .ant-layout-content > div {
          animation: fadeIn 0.3s ease-out;
        }
      `}</style>
    </Layout>
  );
};

export default AppLayout;
