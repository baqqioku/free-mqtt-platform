import React, { useEffect, useState, useCallback } from 'react';
import {
  Table,
  Tag,
  Button,
  Space,
  Card,
  Input,
  Typography,
  Row,
  Col,
  Alert,
  Modal,
  Descriptions,
  message,
  Tooltip,
  Popconfirm
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  SearchOutlined,
  ReloadOutlined,
  UserOutlined,
  WifiOutlined,
  DisconnectOutlined,
  InfoCircleOutlined,
  DeleteOutlined,
  GlobalOutlined,
  ExperimentOutlined
} from '@ant-design/icons';
import { routeServiceApi, ClientInfo } from '../api';

const { Text } = Typography;

interface ClientRecord extends ClientInfo {
  key: string;
}

const Clients: React.FC = () => {
  const [clients, setClients] = useState<ClientRecord[]>([]);
  const [filteredClients, setFilteredClients] = useState<ClientRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchText, setSearchText] = useState('');
  const [selectedClient, setSelectedClient] = useState<ClientRecord | null>(null);
  const [modalVisible, setModalVisible] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [resetting, setResetting] = useState(false);

  const fetchClients = useCallback(async () => {
    setLoading(true);
    try {
      const res = await routeServiceApi.getClients();
      if (res?.dataBody) {
        const data = (res.dataBody as ClientInfo[]).map(c => ({
          ...c,
          key: String(c.userId)
        }));
        setClients(data);
        setFilteredClients(data);
      }
      setError(null);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch clients');
      message.error('Failed to load clients');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchClients();
  }, [fetchClients]);

  // Search filter
  useEffect(() => {
    if (searchText.trim() === '') {
      setFilteredClients(clients);
    } else {
      const lower = searchText.toLowerCase();
      setFilteredClients(
        clients.filter(c =>
          c.userName?.toLowerCase().includes(lower) ||
          c.brokerName?.toLowerCase().includes(lower) ||
          String(c.userId).includes(lower)
        )
      );
    }
  }, [searchText, clients]);

  const showDetail = (record: ClientRecord) => {
    setSelectedClient(record);
    setModalVisible(true);
  };

  const handleKick = async (userId: number, userName: string) => {
    try {
      await routeServiceApi.offerLine(userId);
      message.success(`User "${userName}" disconnected`);
      fetchClients();
    } catch (err: any) {
      message.error('Failed to disconnect user');
    }
  };

  const handleResetTestData = async () => {
    setResetting(true);
    try {
      const res = await routeServiceApi.resetTestData();
      if (res?.dataBody) {
        const d = res.dataBody as any;
        message.success(`Reset done: ${d.createdUsers} users created, ${d.onlineUsers} online, ${d.deletedKeys} old keys deleted`);
      } else {
        message.success('Test data reset successfully');
      }
      fetchClients();
    } catch (err: any) {
      message.error(err?.response?.data?.message || err.message || 'Failed to reset test data');
    } finally {
      setResetting(false);
    }
  };

  const columns: ColumnsType<ClientRecord> = [
    {
      title: 'User ID',
      dataIndex: 'userId',
      key: 'userId',
      width: 100,
      render: (id: number) => <Text code>{id}</Text>,
      sorter: (a, b) => a.userId - b.userId,
    },
    {
      title: 'User Name',
      dataIndex: 'userName',
      key: 'userName',
      render: (name: string) => (
        <Text code copyable={{ text: name }}>
          <UserOutlined style={{ marginRight: 8 }} />
          {name || '-'}
        </Text>
      ),
      sorter: (a, b) => (a.userName || '').localeCompare(b.userName || ''),
    },
    {
      title: 'Status',
      dataIndex: 'online',
      key: 'online',
      width: 110,
      render: (online: boolean) =>
        online ? (
          <Tag icon={<WifiOutlined />} color="success" style={{ borderRadius: 12 }}>
            Online
          </Tag>
        ) : (
          <Tag icon={<DisconnectOutlined />} color="default" style={{ borderRadius: 12 }}>
            Offline
          </Tag>
        ),
      filters: [
        { text: 'Online', value: true },
        { text: 'Offline', value: false }
      ],
      onFilter: (value, record) => record.online === value,
    },
    {
      title: 'Assigned Broker',
      dataIndex: 'brokerName',
      key: 'brokerName',
      width: 160,
      render: (broker: string, record) =>
        broker ? (
          <Space>
            <GlobalOutlined />
            <Text code>{broker}</Text>
          </Space>
        ) : (
          <Text type="secondary">-</Text>
        ),
    },
    {
      title: 'Broker Address',
      key: 'brokerAddr',
      width: 200,
      render: (_, record) =>
        record.brokerIp ? (
          <Text type="secondary">
            {record.brokerIp}:{record.brokerTcpPort} (TCP) / :{record.brokerHttpPort} (HTTP)
          </Text>
        ) : (
          <Text type="secondary">-</Text>
        ),
    },
    {
      title: 'Actions',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="View Details">
            <Button
              type="link"
              size="small"
              icon={<InfoCircleOutlined />}
              onClick={() => showDetail(record)}
            >
              Detail
            </Button>
          </Tooltip>
          {record.online && (
            <Popconfirm
              title={`Disconnect user "${record.userName}"?`}
              description="This will remove the user's broker assignment and mark them offline."
              onConfirm={() => handleKick(record.userId, record.userName)}
              okText="Yes"
              cancelText="No"
            >
              <Button
                type="link"
                size="small"
                danger
                icon={<DisconnectOutlined />}
              >
                Kick
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  const onlineCount = clients.filter(c => c.online).length;
  const offlineCount = clients.filter(c => !c.online).length;

  return (
    <div style={{ padding: '0 8px' }}>
      {error && (
        <Alert
          message="Error loading clients"
          description={error}
          type="error"
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
                <UserOutlined style={{ marginRight: 8 }} />
                MQTT Clients Management
              </Text>
              <Tag color="blue">{filteredClients.length} Total</Tag>
              <Tag color="green">{onlineCount} Online</Tag>
              <Tag color="default">{offlineCount} Offline</Tag>
            </Space>
          </Col>
          <Col>
            <Space>
              <Input
                placeholder="Search by name/broker/userId..."
                prefix={<SearchOutlined />}
                allowClear
                value={searchText}
                onChange={e => setSearchText(e.target.value)}
                style={{ width: 260 }}
              />
              <Button
                type="primary"
                icon={<ReloadOutlined />}
                onClick={fetchClients}
                loading={loading}
              >
                Refresh
              </Button>
              <Popconfirm
                title="Reset all test data?"
                description="This will DELETE all existing users and create fresh test users (6 online, 4 offline)."
                onConfirm={handleResetTestData}
                okText="Yes, Reset"
                cancelText="Cancel"
                okButtonProps={{ danger: true }}
              >
                <Button
                  icon={<ExperimentOutlined />}
                  loading={resetting}
                >
                  Reset Test Data
                </Button>
              </Popconfirm>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* Table */}
      <Card style={{ marginTop: 16 }}>
        <Table
          columns={columns}
          dataSource={filteredClients}
          rowKey="userId"
          loading={loading}
          pagination={{
            pageSize: 10,
            showTotal: (total) => `Total ${total} clients`,
            pageSizeOptions: ['10', '20', '50'],
            showSizeChanger: true
          }}
          scroll={{ x: 900 }}
          size="middle"
          locale={{
            emptyText: (
              <div style={{ padding: 40, textAlign: 'center' }}>
                <WifiOutlined style={{ fontSize: 48, color: '#ccc', marginBottom: 16 }} />
                <p>No registered clients found</p>
                <Text type="secondary">Clients will appear here once they register through the Route service</Text>
              </div>
            )
          }}
        />
      </Card>

      {/* Detail Modal */}
      <Modal
        title={
          <>
            <InfoCircleOutlined /> Client Details
          </>
        }
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setModalVisible(false)}>
            Close
          </Button>,
          selectedClient?.online && (
            <Button
              key="kick"
              danger
              onClick={() => {
                handleKick(selectedClient!.userId, selectedClient!.userName);
                setModalVisible(false);
              }}
            >
              Disconnect
            </Button>
          )
        ]}
        width={600}
      >
        {selectedClient && (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="User ID">
              <Text code>{selectedClient.userId}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="User Name">
              <Text code>{selectedClient.userName}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Status">
              {selectedClient.online ? (
                <Tag color="success">Online</Tag>
              ) : (
                <Tag color="default">Offline</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="Assigned Broker">
              {selectedClient.brokerName ? (
                <Text code>{selectedClient.brokerName}</Text>
              ) : (
                <Text type="secondary">Not assigned</Text>
              )}
            </Descriptions.Item>
            {selectedClient.brokerIp && (
              <>
                <Descriptions.Item label="Broker IP">
                  <Text code>{selectedClient.brokerIp}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="Broker TCP Port">
                  {selectedClient.brokerTcpPort}
                </Descriptions.Item>
                <Descriptions.Item label="Broker HTTP Port">
                  {selectedClient.brokerHttpPort}
                </Descriptions.Item>
              </>
            )}
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default Clients;
