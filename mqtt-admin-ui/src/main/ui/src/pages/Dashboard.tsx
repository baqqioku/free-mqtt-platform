import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Progress,
  Row,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ApiOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  ReloadOutlined,
  RiseOutlined,
  WifiOutlined,
} from '@ant-design/icons';
import { adminApi, ClientInfo, ClusterInfo, getErrorMessage, routeServiceApi, SystemStats } from '../api';
import { PROJECT_SERVICES } from '../constants/platform';
import { formatBrokerAddress, formatDateTime, formatHours, formatRelativeAge } from '../utils/format';

const { Title, Text } = Typography;

const Dashboard: React.FC = () => {
  const [stats, setStats] = useState<SystemStats | null>(null);
  const [clients, setClients] = useState<ClientInfo[]>([]);
  const [cluster, setCluster] = useState<ClusterInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [updatedAt, setUpdatedAt] = useState<number | null>(null);

  const loadDashboard = async (silent = false) => {
    if (silent) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }

    try {
      const [statsData, clientData, clusterData] = await Promise.all([
        adminApi.getStats(),
        routeServiceApi.getClients(),
        routeServiceApi.getCluster(),
      ]);

      setStats(statsData ?? null);
      setClients(clientData ?? []);
      setCluster(clusterData ?? null);
      setUpdatedAt(Date.now());
      setError(null);
    } catch (loadError) {
      setError(getErrorMessage(loadError, 'Failed to load dashboard data'));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    void loadDashboard();
    const timer = window.setInterval(() => {
      void loadDashboard(true);
    }, 10000);

    return () => window.clearInterval(timer);
  }, []);

  const onlineClients = stats?.onlineClients ?? clients.filter((client) => client.online).length;
  const totalClients = stats?.totalClients ?? clients.length;
  const offlineClients = Math.max(totalClients - onlineClients, 0);
  const brokerCount = cluster?.brokerCount ?? cluster?.brokers.length ?? 0;
  const clientRatio = totalClients > 0 ? Math.round((onlineClients / totalClients) * 100) : 0;

  const clientColumns: ColumnsType<ClientInfo> = [
    {
      title: 'User',
      dataIndex: 'userName',
      key: 'userName',
      render: (value: string) => <Text code>{value || '-'}</Text>,
    },
    {
      title: 'Status',
      dataIndex: 'online',
      key: 'online',
      width: 120,
      render: (online: boolean) =>
        online ? (
          <Tag icon={<WifiOutlined />} color="success">
            Online
          </Tag>
        ) : (
          <Tag color="default">Offline</Tag>
        ),
    },
    {
      title: 'Broker',
      key: 'broker',
      render: (_, record) =>
        record.brokerName ? (
          <Space direction="vertical" size={0}>
            <Tag color="blue">{record.brokerName}</Tag>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {formatBrokerAddress(record.brokerIp, record.brokerTcpPort, record.brokerHttpPort)}
            </Text>
          </Space>
        ) : (
          <Text type="secondary">Unassigned</Text>
        ),
    },
  ];

  if (error && !loading) {
    return (
      <Alert
        message="Dashboard data is unavailable"
        description={error}
        type="error"
        showIcon
        action={
          <Button type="primary" icon={<ReloadOutlined />} onClick={() => void loadDashboard()}>
            Retry
          </Button>
        }
      />
    );
  }

  return (
    <Spin spinning={loading}>
      <div style={{ padding: '0 8px' }}>
        <Row justify="space-between" align="middle" style={{ marginBottom: 24 }}>
          <Col>
            <Space direction="vertical" size={2}>
              <Title level={3} style={{ margin: 0 }}>
                <ApiOutlined style={{ marginRight: 12 }} />
                MQTT Operations Dashboard
              </Title>
              <Text type="secondary">
                Registered clients, broker topology, and runtime health in one place.
              </Text>
            </Space>
          </Col>
          <Col>
            <Space>
              <Text type="secondary">Last synced {formatRelativeAge(updatedAt)}</Text>
              <Button
                icon={<ReloadOutlined spin={refreshing} />}
                onClick={() => void loadDashboard(true)}
                loading={refreshing}
              >
                Refresh
              </Button>
            </Space>
          </Col>
        </Row>

        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} lg={6}>
            <Card hoverable bordered={false} className="stat-card">
              <Statistic
                title={<Text type="secondary">Online Clients</Text>}
                value={onlineClients}
                prefix={<CloudServerOutlined style={{ color: '#3f8600' }} />}
                suffix={`/ ${totalClients}`}
                valueStyle={{ color: '#3f8600', fontSize: 28 }}
              />
              <Progress
                percent={clientRatio}
                size="small"
                showInfo={false}
                strokeColor="#3f8600"
                style={{ marginTop: 8 }}
              />
            </Card>
          </Col>

          <Col xs={24} sm={12} lg={6}>
            <Card hoverable bordered={false} className="stat-card">
              <Statistic
                title={<Text type="secondary">Offline Clients</Text>}
                value={offlineClients}
                prefix={<WifiOutlined style={{ color: '#fa8c16' }} />}
                valueStyle={{ color: '#fa8c16', fontSize: 28 }}
              />
              <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
                Users without an active broker session.
              </Text>
            </Card>
          </Col>

          <Col xs={24} sm={12} lg={6}>
            <Card hoverable bordered={false} className="stat-card">
              <Statistic
                title={<Text type="secondary">Broker Nodes</Text>}
                value={brokerCount}
                prefix={<DatabaseOutlined style={{ color: '#1677ff' }} />}
                valueStyle={{ color: '#1677ff', fontSize: 28 }}
              />
              <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
                Cluster: {cluster?.clusterName || 'default'}
              </Text>
            </Card>
          </Col>

          <Col xs={24} sm={12} lg={6}>
            <Card hoverable bordered={false} className="stat-card">
              <Statistic
                title={<Text type="secondary">System Uptime</Text>}
                value={stats?.uptime ?? 0}
                prefix={<RiseOutlined style={{ color: '#722ed1' }} />}
                suffix="hours"
                valueStyle={{ color: '#722ed1', fontSize: 28 }}
              />
              <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
                Message rate: {stats?.messageRate ?? 0} msg/s
              </Text>
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          <Col xs={24} lg={10}>
            <Card title={<><DatabaseOutlined /> Cluster Overview</>} size="small">
              {cluster?.brokers?.length ? (
                <Space direction="vertical" style={{ width: '100%' }} size="middle">
                  {cluster.brokers.map((broker) => (
                    <Card key={broker.brokerName} size="small" bordered={false} style={{ background: '#fafafa' }}>
                      <Space direction="vertical" size={2}>
                        <Space>
                          <Text strong code>
                            {broker.brokerName}
                          </Text>
                          <Tag color="success">Online</Tag>
                        </Space>
                        <Text type="secondary">{formatBrokerAddress(broker.ip, broker.tcpPort, broker.httpPort)}</Text>
                      </Space>
                    </Card>
                  ))}
                </Space>
              ) : (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="No broker nodes are currently registered"
                />
              )}
            </Card>
          </Col>

          <Col xs={24} lg={14}>
            <Card
              title={<><CloudServerOutlined /> Registered Clients</>}
              size="small"
              extra={<Text type="secondary">{clients.length} records</Text>}
            >
              <Table
                columns={clientColumns}
                dataSource={clients}
                rowKey="userId"
                pagination={false}
                size="small"
                scroll={{ y: 280 }}
                locale={{ emptyText: 'No clients have been registered yet.' }}
              />
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]}>
          <Col xs={24} lg={12}>
            <Card title="Runtime Details" size="small">
              <Descriptions column={1} size="small">
                <Descriptions.Item label="Broker HTTP API">
                  <Text code>http://localhost:{PROJECT_SERVICES.broker.httpPort}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="Route Service">
                  <Text code>http://localhost:{PROJECT_SERVICES.route.httpPort}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="Message Counter">
                  {stats?.messagesToday ?? 0} today
                </Descriptions.Item>
                <Descriptions.Item label="Last Broker Refresh">
                  {formatDateTime(cluster?.refreshTime ?? null)}
                </Descriptions.Item>
                <Descriptions.Item label="Server Timestamp">
                  {formatDateTime(stats?.timestamp ?? null)}
                </Descriptions.Item>
              </Descriptions>
            </Card>
          </Col>

          <Col xs={24} lg={12}>
            <Card title="Service Endpoints" size="small">
              <Descriptions column={1} size="small">
                <Descriptions.Item label="MQTT TCP">
                  <Text code>{PROJECT_SERVICES.broker.tcpPort}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="MQTT WebSocket">
                  <Text code>{PROJECT_SERVICES.broker.websocketPort}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="Broker HTTP">
                  <Text code>{PROJECT_SERVICES.broker.httpPort}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="Route HTTP">
                  <Text code>{PROJECT_SERVICES.route.httpPort}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="Redis / ZooKeeper">
                  <Text code>
                    {PROJECT_SERVICES.redis.port} / {PROJECT_SERVICES.zookeeper.port}
                  </Text>
                </Descriptions.Item>
                <Descriptions.Item label="Uptime (friendly)">
                  {formatHours(stats?.uptime)}
                </Descriptions.Item>
              </Descriptions>
            </Card>
          </Col>
        </Row>
      </div>
    </Spin>
  );
};

export default Dashboard;

