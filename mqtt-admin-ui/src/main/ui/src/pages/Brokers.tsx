import React, { useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  message,
  Popconfirm,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloudServerOutlined,
  GlobalOutlined,
  ReloadOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { BrokerNode, ClientInfo, ClusterInfo, getErrorMessage, routeServiceApi } from '../api';
import { formatDateTime, formatRelativeAge } from '../utils/format';

const { Text } = Typography;

interface BrokerUsage {
  total: number;
  online: number;
}

const Brokers: React.FC = () => {
  const [clusterInfo, setClusterInfo] = useState<ClusterInfo | null>(null);
  const [clients, setClients] = useState<ClientInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadCluster = async () => {
    setLoading(true);
    try {
      const [cluster, clientList] = await Promise.all([
        routeServiceApi.getCluster(),
        routeServiceApi.getClients(),
      ]);
      setClusterInfo(cluster ?? null);
      setClients(clientList ?? []);
      setError(null);
    } catch (loadError) {
      setError(getErrorMessage(loadError, 'Failed to load cluster info'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadCluster();
    const timer = window.setInterval(() => {
      void loadCluster();
    }, 15000);

    return () => window.clearInterval(timer);
  }, []);

  const brokerUsage = clients.reduce<Record<string, BrokerUsage>>((acc, client) => {
    if (!client.brokerName) {
      return acc;
    }

    if (!acc[client.brokerName]) {
      acc[client.brokerName] = { total: 0, online: 0 };
    }

    acc[client.brokerName].total += 1;
    if (client.online) {
      acc[client.brokerName].online += 1;
    }
    return acc;
  }, {});

  const handleMarkDown = async (brokerName: string) => {
    try {
      await routeServiceApi.markBrokerDown(brokerName);
      message.success(`Broker "${brokerName}" has been removed from the route pool for a cooldown window.`);
      await loadCluster();
    } catch (markError) {
      message.error(getErrorMessage(markError, 'Failed to mark broker down'));
    }
  };

  const columns: ColumnsType<BrokerNode> = [
    {
      title: 'Broker Name',
      dataIndex: 'brokerName',
      key: 'brokerName',
      render: (name: string) => (
        <Space>
          <CloudServerOutlined />
          <Text strong code>
            {name}
          </Text>
        </Space>
      ),
      sorter: (a, b) => (a.brokerName || '').localeCompare(b.brokerName || ''),
    },
    {
      title: 'Status',
      key: 'status',
      width: 120,
      render: () => (
        <Tag icon={<CheckCircleOutlined />} color="success">
          Online
        </Tag>
      ),
    },
    {
      title: 'Address',
      dataIndex: 'ip',
      key: 'ip',
      render: (ip: string) => (
        <Space>
          <GlobalOutlined />
          <Text>{ip || '-'}</Text>
        </Space>
      ),
    },
    {
      title: 'MQTT Port',
      dataIndex: 'tcpPort',
      key: 'tcpPort',
      width: 120,
      align: 'center',
      render: (port: number) => <Tag color="green">TCP {port}</Tag>,
    },
    {
      title: 'HTTP Port',
      dataIndex: 'httpPort',
      key: 'httpPort',
      width: 120,
      align: 'center',
      render: (port: number) => <Tag color="blue">HTTP {port}</Tag>,
    },
    {
      title: 'Assigned Clients',
      key: 'assignedClients',
      width: 150,
      render: (_, record) => {
        const usage = brokerUsage[record.brokerName] || { total: 0, online: 0 };
        return (
          <Space direction="vertical" size={0}>
            <Text strong>{usage.total}</Text>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {usage.online} online
            </Text>
          </Space>
        );
      },
    },
    {
      title: 'Actions',
      key: 'action',
      width: 140,
      render: (_, record) => (
        <Popconfirm
          title={`Mark "${record.brokerName}" down?`}
          description="The route service will temporarily stop assigning users to this broker."
          onConfirm={() => void handleMarkDown(record.brokerName)}
          okText="Mark Down"
          cancelText="Cancel"
        >
          <Button type="link" size="small" danger icon={<StopOutlined />}>
            Mark Down
          </Button>
        </Popconfirm>
      ),
    },
  ];

  const totalAssignedClients = Object.values(brokerUsage).reduce((sum, usage) => sum + usage.total, 0);

  return (
    <div style={{ padding: '0 8px' }}>
      {error && (
        <Alert
          message="Cluster data is unavailable"
          description={error}
          type="warning"
          showIcon
          closable
          onClose={() => setError(null)}
          style={{ marginBottom: 16 }}
        />
      )}

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic title="Cluster Name" value={clusterInfo?.clusterName || 'default'} />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic title="Broker Nodes" value={clusterInfo?.brokerCount ?? 0} prefix={<CloudServerOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic title="Assigned Clients" value={totalAssignedClients} valueStyle={{ color: '#1677ff' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic
              title="Last Refresh"
              value={formatRelativeAge(clusterInfo?.refreshTime ?? null)}
              valueStyle={{ fontSize: 24 }}
            />
          </Card>
        </Col>
      </Row>

      <Card>
        <Row justify="space-between" align="middle">
          <Col>
            <Space size="large">
              <Text strong style={{ fontSize: 16 }}>
                <CloudServerOutlined style={{ marginRight: 8 }} />
                Broker Cluster Management
              </Text>
              <Tag color="blue">{clusterInfo?.clusterName || 'default'}</Tag>
              <Tag color="processing">{clusterInfo?.brokerCount ?? 0} nodes</Tag>
            </Space>
          </Col>
          <Col>
            <Button type="primary" icon={<ReloadOutlined />} onClick={() => void loadCluster()} loading={loading}>
              Refresh
            </Button>
          </Col>
        </Row>
      </Card>

      <Card size="small" style={{ marginTop: 16 }}>
        <Descriptions column={{ xs: 1, sm: 2, md: 4 }} size="small">
          <Descriptions.Item label="Cluster Name">
            <Text code>{clusterInfo?.clusterName || 'default'}</Text>
          </Descriptions.Item>
          <Descriptions.Item label="Broker Count">
            <Badge count={clusterInfo?.brokerCount ?? 0} style={{ backgroundColor: '#52c41a' }} overflowCount={999} />
          </Descriptions.Item>
          <Descriptions.Item label="Last Refresh">
            {formatDateTime(clusterInfo?.refreshTime ?? null)}
          </Descriptions.Item>
          <Descriptions.Item label="Assigned Clients">
            {totalAssignedClients}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card style={{ marginTop: 16 }}>
        <Table
          columns={columns}
          dataSource={clusterInfo?.brokers || []}
          rowKey="brokerName"
          loading={loading}
          pagination={false}
          locale={{
            emptyText: loading ? null : (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="No broker nodes are registered with the route service."
              />
            ),
          }}
        />
      </Card>

      <Card title="Operational Note" size="small" style={{ marginTop: 16 }}>
        <Space direction="vertical" size={4}>
          <Text>
            <ClockCircleOutlined style={{ marginRight: 8 }} />
            Marking a broker down is an operational override for the route pool. It is useful for failover drills and maintenance windows.
          </Text>
          <Text type="secondary">
            The page combines cluster topology with client assignments, so you can immediately see whether a broker is still carrying user traffic.
          </Text>
        </Space>
      </Card>
    </div>
  );
};

export default Brokers;

