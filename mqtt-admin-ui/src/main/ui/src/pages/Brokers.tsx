import React, { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Table,
  Tag,
  Button,
  Space,
  Row,
  Col,
  Typography,
  Alert,
  Tooltip,
  Badge,
  Descriptions,
  message,
  Popconfirm
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CloudServerOutlined,
  ReloadOutlined,
  ApiOutlined,
  GlobalOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  StopOutlined
} from '@ant-design/icons';
import { routeServiceApi, ClusterInfo, BrokerNode } from '../api';

const { Text } = Typography;

const Brokers: React.FC = () => {
  const [clusterInfo, setClusterInfo] = useState<ClusterInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchCluster = useCallback(async () => {
    setLoading(true);
    try {
      const res = await routeServiceApi.getCluster();
      if (res?.dataBody) {
        setClusterInfo(res.dataBody as ClusterInfo);
        setError(null);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to get cluster info');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCluster();
    const timer = setInterval(fetchCluster, 15000);
    return () => clearInterval(timer);
  }, [fetchCluster]);

  const handleMarkDown = async (brokerName: string) => {
    try {
      await routeServiceApi.markBrokerDown(brokerName);
      message.success(`Broker "${brokerName}" marked as down`);
      fetchCluster();
    } catch (err: any) {
      message.error('Failed to mark broker down');
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
          <Text strong code>{name}</Text>
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
      title: 'IP Address',
      dataIndex: 'ip',
      key: 'ip',
      render: (ip: string) => (
        <Space><GlobalOutlined />{ip || '-'}</Space>
      ),
    },
    {
      title: 'MQTT Port',
      dataIndex: 'tcpPort',
      key: 'tcpPort',
      width: 110,
      align: 'center',
      render: (port: number) => (
        <Tag color="green">TCP:{port}</Tag>
      ),
    },
    {
      title: 'HTTP Port',
      dataIndex: 'httpPort',
      key: 'httpPort',
      width: 110,
      align: 'center',
      render: (port: number) => (
        <Tag color="blue">HTTP:{port}</Tag>
      ),
    },
    {
      title: 'Actions',
      key: 'action',
      width: 120,
      render: (_, record) => (
        <Popconfirm
          title={`Mark broker "${record.brokerName}" as down?`}
          description="This will temporarily remove it from the routing pool for 30 seconds."
          onConfirm={() => handleMarkDown(record.brokerName)}
          okText="Yes"
          cancelText="No"
        >
          <Button
            type="link"
            size="small"
            danger
            icon={<StopOutlined />}
          >
            Mark Down
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <div style={{ padding: '0 8px' }}>
      {error && (
        <Alert
          message="Error"
          description={error}
          type="warning"
          showIcon
          closable
          onClose={() => setError(null)}
          style={{ marginBottom: 16 }}
        />
      )}

      {/* Header */}
      <Card>
        <Row justify="space-between" align="middle">
          <Col>
            <Space size="large">
              <Text strong style={{ fontSize: 16 }}>
                <CloudServerOutlined style={{ marginRight: 8 }} />
                Broker Cluster Management
              </Text>
              {clusterInfo && (
                <>
                  <Tag color="blue">{clusterInfo.clusterName || 'default'}</Tag>
                  <Tag color="processing">
                    {clusterInfo.brokerCount} Nodes
                  </Tag>
                </>
              )}
            </Space>
          </Col>
          <Col>
            <Button
              type="primary"
              icon={<ReloadOutlined />}
              onClick={fetchCluster}
              loading={loading}
            >
              Refresh
            </Button>
          </Col>
        </Row>
      </Card>

      {/* Cluster Info Summary */}
      {!loading && clusterInfo && (
        <Card size="small" style={{ marginTop: 16 }} bordered>
          <Descriptions column={{ xs: 1, sm: 2, md: 4 }} size="small">
            <Descriptions.Item label="Cluster Name">
              <Text code>{clusterInfo.clusterName || 'default'}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Total Nodes">
              <Badge count={clusterInfo.brokerCount} style={{ backgroundColor: '#52c41a' }}
                overflowCount={999} />
            </Descriptions.Item>
            <Descriptions.Item label="Last Refresh">
              {clusterInfo.refreshTime > 0
                ? new Date(clusterInfo.refreshTime).toLocaleString()
                : 'N/A'}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {/* Table */}
      <Card style={{ marginTop: 16 }}>
        <Table
          columns={columns}
          dataSource={clusterInfo?.brokers || []}
          rowKey="brokerName"
          loading={loading}
          pagination={false}
          locale={{
            emptyText: loading ? null : (
              <div style={{ padding: 40, textAlign: 'center' }}>
                <ClockCircleOutlined style={{ fontSize: 48, color: '#ccc', marginBottom: 16 }} />
                <p>No broker nodes found</p>
                <Text type="secondary">
                  Broker nodes will appear here once registered with ZooKeeper
                </Text>
              </div>
            )
          }}
        />
      </Card>
    </div>
  );
};

export default Brokers;
