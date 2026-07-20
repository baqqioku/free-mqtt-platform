import React, { useEffect, useState } from 'react';
import {
  Alert,
  Card,
  Col,
  Descriptions,
  Divider,
  List,
  Row,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ApiOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { adminApi, ClientInfo, ClusterInfo, getErrorMessage, SystemStats, routeServiceApi } from '../api';
import { API_REFERENCE, ApiReferenceItem, KNOWN_LIMITATIONS, PROJECT_SERVICES, REQUEST_EXAMPLES } from '../constants/platform';
import { formatDateTime, formatHours } from '../utils/format';

const { Paragraph, Text, Title } = Typography;

interface RuntimeServiceRow {
  key: string;
  component: string;
  status: string;
  port: string;
  note: string;
}

const Settings: React.FC = () => {
  const [stats, setStats] = useState<SystemStats | null>(null);
  const [cluster, setCluster] = useState<ClusterInfo | null>(null);
  const [clients, setClients] = useState<ClientInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadRuntime = async () => {
    setLoading(true);
    try {
      const [statsData, clusterData, clientsData] = await Promise.all([
        adminApi.getStats(),
        routeServiceApi.getCluster(),
        routeServiceApi.getClients(),
      ]);

      setStats(statsData ?? null);
      setCluster(clusterData ?? null);
      setClients(clientsData ?? []);
      setError(null);
    } catch (loadError) {
      setError(getErrorMessage(loadError, 'Failed to load runtime snapshot'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadRuntime();
  }, []);

  const runtimeRows: RuntimeServiceRow[] = [
    {
      key: 'broker-http',
      component: PROJECT_SERVICES.broker.name,
      status: 'Running',
      port: String(PROJECT_SERVICES.broker.httpPort),
      note: 'Provides /admin/stats and /admin/messages.',
    },
    {
      key: 'broker-tcp',
      component: 'MQTT TCP Listener',
      status: 'Running',
      port: String(PROJECT_SERVICES.broker.tcpPort),
      note: 'Primary device connection endpoint.',
    },
    {
      key: 'broker-ws',
      component: 'MQTT WebSocket',
      status: 'Running',
      port: String(PROJECT_SERVICES.broker.websocketPort),
      note: 'WebSocket transport for browser-friendly MQTT clients.',
    },
    {
      key: 'route-http',
      component: PROJECT_SERVICES.route.name,
      status: 'Running',
      port: String(PROJECT_SERVICES.route.httpPort),
      note: 'Handles register/login, routing, cluster admin, and push operations.',
    },
    {
      key: 'redis',
      component: PROJECT_SERVICES.redis.name,
      status: 'External',
      port: String(PROJECT_SERVICES.redis.port),
      note: 'Stores user registry, broker assignments, and offline delivery data.',
    },
    {
      key: 'zk',
      component: PROJECT_SERVICES.zookeeper.name,
      status: 'External',
      port: String(PROJECT_SERVICES.zookeeper.port),
      note: 'Tracks broker membership when cluster mode is enabled.',
    },
  ];

  const runtimeColumns: ColumnsType<RuntimeServiceRow> = [
    {
      title: 'Component',
      dataIndex: 'component',
      key: 'component',
      render: (value: string) => <Text strong>{value}</Text>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (value: string) => (
        <Tag color={value === 'External' ? 'default' : 'success'}>
          {value}
        </Tag>
      ),
    },
    {
      title: 'Port',
      dataIndex: 'port',
      key: 'port',
      width: 120,
      render: (value: string) => <Text code>{value}</Text>,
    },
    {
      title: 'Note',
      dataIndex: 'note',
      key: 'note',
    },
  ];

  const apiColumns: ColumnsType<ApiReferenceItem> = [
    {
      title: 'Service',
      dataIndex: 'service',
      key: 'service',
      width: 160,
    },
    {
      title: 'Method',
      dataIndex: 'method',
      key: 'method',
      width: 100,
      render: (method: ApiReferenceItem['method']) => (
        <Tag color={method === 'GET' ? 'blue' : 'green'}>{method}</Tag>
      ),
    },
    {
      title: 'Path',
      dataIndex: 'path',
      key: 'path',
      width: 220,
      render: (value: string) => <Text code>{value}</Text>,
    },
    {
      title: 'Description',
      dataIndex: 'description',
      key: 'description',
    },
    {
      title: 'Note',
      dataIndex: 'note',
      key: 'note',
      width: 300,
      render: (value?: string) => <Text type="secondary">{value || '-'}</Text>,
    },
  ];

  return (
    <div style={{ padding: '0 8px' }}>
      {error && (
        <Alert
          type="error"
          showIcon
          message="Runtime snapshot could not be loaded"
          description={error}
          style={{ marginBottom: 16 }}
        />
      )}

      <Card>
        <Row justify="space-between" align="middle">
          <Col>
            <Space direction="vertical" size={2}>
              <Title level={4} style={{ margin: 0 }}>
                <SettingOutlined style={{ marginRight: 12 }} />
                System Settings & API Center
              </Title>
              <Text type="secondary">
                Runtime visibility, endpoint reference, and integration notes for the admin UI.
              </Text>
            </Space>
          </Col>
        </Row>
      </Card>

      <Card style={{ marginTop: 16 }}>
        <Tabs
          items={[
            {
              key: 'runtime',
              label: (
                <>
                  <CloudServerOutlined /> Runtime Snapshot
                </>
              ),
              children: (
                <Space direction="vertical" size="large" style={{ width: '100%' }}>
                  <Alert
                    type="info"
                    showIcon
                    message="The settings page is read-only for now"
                    description="The backend does not expose save endpoints for server, cluster, ACL, or Redis configuration yet. This page focuses on accurate runtime visibility instead of pretending to save changes."
                  />

                  <Row gutter={[16, 16]}>
                    <Col xs={24} sm={12} xl={6}>
                      <Card loading={loading}>
                        <Statistic title="Registered Clients" value={stats?.totalClients ?? clients.length} />
                      </Card>
                    </Col>
                    <Col xs={24} sm={12} xl={6}>
                      <Card loading={loading}>
                        <Statistic title="Online Clients" value={stats?.onlineClients ?? clients.filter((client) => client.online).length} valueStyle={{ color: '#3f8600' }} />
                      </Card>
                    </Col>
                    <Col xs={24} sm={12} xl={6}>
                      <Card loading={loading}>
                        <Statistic title="Broker Nodes" value={cluster?.brokerCount ?? 0} valueStyle={{ color: '#1677ff' }} />
                      </Card>
                    </Col>
                    <Col xs={24} sm={12} xl={6}>
                      <Card loading={loading}>
                        <Statistic title="Uptime" value={formatHours(stats?.uptime)} valueStyle={{ fontSize: 24 }} />
                      </Card>
                    </Col>
                  </Row>

                  <Card title="Current Runtime Detail" loading={loading}>
                    <Descriptions column={{ xs: 1, md: 2 }} size="small">
                      <Descriptions.Item label="Cluster Name">
                        <Text code>{cluster?.clusterName || 'default'}</Text>
                      </Descriptions.Item>
                      <Descriptions.Item label="Broker Refresh Time">
                        {formatDateTime(cluster?.refreshTime ?? null)}
                      </Descriptions.Item>
                      <Descriptions.Item label="Server Timestamp">
                        {formatDateTime(stats?.timestamp ?? null)}
                      </Descriptions.Item>
                      <Descriptions.Item label="Message Rate">
                        {stats?.messageRate ?? 0} msg/s
                      </Descriptions.Item>
                      <Descriptions.Item label="Messages Today">
                        {stats?.messagesToday ?? 0}
                      </Descriptions.Item>
                      <Descriptions.Item label="Cluster Mode">
                        <Tag color={stats?.clusterEnabled ? 'success' : 'default'}>
                          {stats?.clusterEnabled ? 'Enabled' : 'Disabled'}
                        </Tag>
                      </Descriptions.Item>
                    </Descriptions>
                  </Card>

                  <Card title="Service Matrix" loading={loading}>
                    <Table columns={runtimeColumns} dataSource={runtimeRows} pagination={false} size="small" />
                  </Card>
                </Space>
              ),
            },
            {
              key: 'api-reference',
              label: (
                <>
                  <ApiOutlined /> API Reference
                </>
              ),
              children: (
                <Space direction="vertical" size="large" style={{ width: '100%' }}>
                  <Card title="Frontend API Map">
                    <Table columns={apiColumns} dataSource={API_REFERENCE} rowKey="key" pagination={false} />
                  </Card>

                  <Row gutter={[16, 16]}>
                    <Col xs={24} lg={8}>
                      <Card title="Register Request">
                        <Paragraph copyable>
                          <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{REQUEST_EXAMPLES.register}</pre>
                        </Paragraph>
                      </Card>
                    </Col>
                    <Col xs={24} lg={8}>
                      <Card title="Login Request">
                        <Paragraph copyable>
                          <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{REQUEST_EXAMPLES.login}</pre>
                        </Paragraph>
                      </Card>
                    </Col>
                    <Col xs={24} lg={8}>
                      <Card title="Push Message Request">
                        <Paragraph copyable>
                          <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{REQUEST_EXAMPLES.pushMessage}</pre>
                        </Paragraph>
                      </Card>
                    </Col>
                  </Row>
                </Space>
              ),
            },
            {
              key: 'notes',
              label: (
                <>
                  <DatabaseOutlined /> Integration Notes
                </>
              ),
              children: (
                <Space direction="vertical" size="large" style={{ width: '100%' }}>
                  <Card title="Known Limitations">
                    <List
                      dataSource={KNOWN_LIMITATIONS}
                      renderItem={(item) => <List.Item>{item}</List.Item>}
                    />
                  </Card>

                  <Card title="Recommended Next Backend Steps">
                    <Paragraph>
                      1. Implement server-side message-history storage behind <Text code>/admin/messages</Text>.
                    </Paragraph>
                    <Paragraph>
                      2. Expose read and write endpoints for MQTT server config, cluster config, ACL config, and Redis settings.
                    </Paragraph>
                    <Paragraph>
                      3. Add a side-effect-free broker preview endpoint so the UI can inspect routing without changing user state.
                    </Paragraph>
                    <Divider />
                    <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                      The current frontend is already wired so it can consume those endpoints with minimal follow-up work.
                    </Paragraph>
                  </Card>
                </Space>
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
};

export default Settings;

