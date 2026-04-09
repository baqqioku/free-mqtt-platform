import React, { useEffect, useState, useCallback } from 'react';
import { Card, Row, Col, Statistic, Table, Tag, Spin, Alert, Typography, Progress } from 'antd';
import {
  CloudServerOutlined,
  MessageOutlined,
  RiseOutlined,
  ClockCircleOutlined,
  WifiOutlined,
  DatabaseOutlined,
  ApiOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import { adminApi, routeServiceApi, ClientInfo } from '../api';

const { Title, Text } = Typography;

interface DashboardStats {
  totalClients: number;
  onlineClients: number;
  messagesToday: number;
  messageRate: number;
  uptime: number;
  timestamp: number;
}

const Dashboard: React.FC = () => {
  const [stats, setStats] = useState<DashboardStats>({
    totalClients: 0,
    onlineClients: 0,
    messagesToday: 0,
    messageRate: 0,
    uptime: 0,
    timestamp: Date.now()
  });
  const [clients, setClients] = useState<ClientInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchStats = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [statsRes, clientsRes] = await Promise.all([
        adminApi.getStats(),
        routeServiceApi.getClients()
      ]);

      if (statsRes?.dataBody) {
        const data = statsRes.dataBody as any;
        setStats({
          totalClients: data.totalClients || 0,
          onlineClients: data.onlineClients || 0,
          messagesToday: data.messagesToday || 0,
          messageRate: data.messageRate || 0,
          uptime: Math.floor((Date.now() - (data.timestamp || Date.now())) / 3600000),
          timestamp: data.timestamp || Date.now()
        });
      }

      if (clientsRes?.dataBody) {
        setClients(clientsRes.dataBody);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to fetch data');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchStats();
    const timer = setInterval(fetchStats, 10000);
    return () => clearInterval(timer);
  }, [fetchStats]);

  const clientColumns = [
    {
      title: 'User',
      dataIndex: 'userName',
      key: 'userName',
      render: (name: string) => <Text code>{name || '-'}</Text>
    },
    {
      title: 'Status',
      dataIndex: 'online',
      key: 'online',
      render: (online: boolean) =>
        online ? (
          <Tag icon={<WifiOutlined />} color="success">Online</Tag>
        ) : (
          <Tag color="default">Offline</Tag>
        )
    },
    {
      title: 'Broker',
      dataIndex: 'brokerName',
      key: 'brokerName',
      render: (name: string) => name ? <Tag color="blue">{name}</Tag> : <Text type="secondary">-</Text>
    }
  ];

  const clientRatio = stats.totalClients > 0
    ? Math.round((stats.onlineClients / stats.totalClients) * 100)
    : 0;

  if (error) {
    return (
      <Alert
        message="Connection Error"
        description={error}
        type="error"
        showIcon
        action={
          <ReloadOutlined onClick={fetchStats} style={{ cursor: 'pointer', fontSize: 18 }} />
        }
      />
    );
  }

  return (
    <Spin spinning={loading}>
      <div style={{ padding: '0 8px' }}>
        {/* Header */}
        <Row justify="space-between" align="middle" style={{ marginBottom: 24 }}>
          <Col>
            <Title level={3} style={{ margin: 0 }}>
              <ApiOutlined style={{ marginRight: 12 }} />
              MQTT Admin Dashboard
            </Title>
          </Col>
          <Col>
            <ReloadOutlined 
              onClick={fetchStats} 
              style={{ cursor: 'pointer', fontSize: 20 }}
              spin={loading}
            />
          </Col>
        </Row>

        {/* Stats Cards */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} lg={6}>
            <Card hoverable bordered={false} className="stat-card">
              <Statistic
                title={<Text type="secondary">Online Clients</Text>}
                value={stats.onlineClients}
                prefix={<CloudServerOutlined style={{ color: '#3f8600' }} />}
                suffix={`/ ${stats.totalClients}`}
                valueStyle={{ color: '#3f8600', fontSize: 28 }}
              />
              <Progress percent={clientRatio} size="small" showInfo={false} strokeColor="#3f8600" style={{ marginTop: 8 }} />
            </Card>
          </Col>

          <Col xs={24} sm={12} lg={6}>
            <Card hoverable bordered={false} className="stat-card">
              <Statistic
                title={<Text type="secondary">Messages Today</Text>}
                value={stats.messagesToday}
                prefix={<MessageOutlined style={{ color: '#cf1322' }} />}
                valueStyle={{ color: '#cf1322', fontSize: 28 }}
              />
            </Card>
          </Col>

          <Col xs={24} sm={12} lg={6}>
            <Card hoverable bordered={false} className="stat-card">
              <Statistic
                title={<Text type="secondary">Message Rate</Text>}
                value={stats.messageRate}
                prefix={<RiseOutlined style={{ color: '#1890ff' }} />}
                suffix="msg/s"
                valueStyle={{ color: '#1890ff', fontSize: 28 }}
              />
            </Card>
          </Col>

          <Col xs={24} sm={12} lg={6}>
            <Card hoverable bordered={false} className="stat-card">
              <Statistic
                title={<Text type="secondary">System Uptime</Text>}
                value={stats.uptime}
                prefix={<ClockCircleOutlined style={{ color: '#722ed1' }} />}
                suffix="hours"
                valueStyle={{ color: '#722ed1', fontSize: 28 }}
              />
            </Card>
          </Col>
        </Row>

        {/* System Info & Recent Clients */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} lg={10}>
            <Card
              title={<><DatabaseOutlined /> System Information</>}
              size="small"
            >
              <Row gutter={[8, 16]}>
                <Col span={12}>
                  <Text strong>MQTT Broker:</Text><br/>
                  <Text type="secondary">TCP Port: 23242</Text>
                </Col>
                <Col span={12}>
                  <Text strong>WebSocket:</Text><br/>
                  <Text type="secondary">WS Port: 8083</Text>
                </Col>
                <Col span={12}>
                  <Text strong>HTTP API:</Text><br/>
                  <Text type="secondary">Port: 23240</Text>
                </Col>
                <Col span={12}>
                  <Text strong>Route Service:</Text><br/>
                  <Text type="secondary">Port: 8084</Text>
                </Col>
                <Col span={12}>
                  <Text strong>ZooKeeper:</Text><br/>
                  <Text type="secondary">Port: 2181</Text>
                </Col>
                <Col span={12}>
                  <Text strong>Redis:</Text><br/>
                  <Text type="secondary">Port: 6379</Text>
                </Col>
              </Row>
            </Card>
          </Col>

          <Col xs={24} lg={14}>
            <Card
              title={<><CloudServerOutlined /> Connected Clients ({clients.length})</>}
              size="small"
              extra={
                <ReloadOutlined onClick={fetchStats} style={{ cursor: 'pointer' }} />
              }
            >
              <Table
                columns={clientColumns}
                dataSource={clients}
                rowKey="userId"
                pagination={false}
                size="small"
                scroll={{ y: 200 }}
                locale={{ emptyText: 'No connected clients' }}
              />
            </Card>
          </Col>
        </Row>
      </div>

      {/* Global Styles */}
      <style>{`
        .stat-card:hover {
          transform: translateY(-4px);
          transition: all 0.3s ease;
          box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        }
        .ant-statistic-content-value {
          font-weight: 600 !important;
        }
      `}</style>
    </Spin>
  );
};

export default Dashboard;
