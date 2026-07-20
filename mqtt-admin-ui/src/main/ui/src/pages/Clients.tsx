import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  DisconnectOutlined,
  ExperimentOutlined,
  InfoCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  UserOutlined,
  WifiOutlined,
} from '@ant-design/icons';
import { ClientInfo, getErrorMessage, RegisteredUser, ResetTestDataResult, routeServiceApi } from '../api';
import { formatBrokerAddress } from '../utils/format';

const { Text } = Typography;

interface ClientRecord extends ClientInfo {
  key: string;
}

const Clients: React.FC = () => {
  const [clients, setClients] = useState<ClientRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchText, setSearchText] = useState('');
  const [selectedClient, setSelectedClient] = useState<ClientRecord | null>(null);
  const [detailVisible, setDetailVisible] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resetting, setResetting] = useState(false);
  const [registerVisible, setRegisterVisible] = useState(false);
  const [registering, setRegistering] = useState(false);
  const [registeredUser, setRegisteredUser] = useState<RegisteredUser | null>(null);
  const [registerForm] = Form.useForm<{ userName: string }>();

  const loadClients = async () => {
    setLoading(true);
    try {
      const response = await routeServiceApi.getClients();
      const data = (response ?? []).map((client) => ({
        ...client,
        key: String(client.userId),
      }));
      setClients(data);
      setError(null);
    } catch (loadError) {
      setError(getErrorMessage(loadError, 'Failed to load clients'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadClients();
    const timer = window.setInterval(() => {
      void loadClients();
    }, 15000);

    return () => window.clearInterval(timer);
  }, []);

  const filteredClients = clients.filter((client) => {
    if (!searchText.trim()) {
      return true;
    }

    const keyword = searchText.toLowerCase();
    return (
      client.userName?.toLowerCase().includes(keyword) ||
      client.brokerName?.toLowerCase().includes(keyword) ||
      String(client.userId).includes(keyword)
    );
  });

  const onlineCount = clients.filter((client) => client.online).length;
  const offlineCount = clients.length - onlineCount;
  const assignedCount = clients.filter((client) => client.brokerName).length;

  const showDetail = (record: ClientRecord) => {
    setSelectedClient(record);
    setDetailVisible(true);
  };

  const handleDisconnect = async (record: ClientRecord) => {
    try {
      await routeServiceApi.disconnectUser(record.userId);
      message.success(`User "${record.userName}" has been marked offline.`);
      await loadClients();
      if (selectedClient?.userId === record.userId) {
        setDetailVisible(false);
      }
    } catch (disconnectError) {
      message.error(getErrorMessage(disconnectError, 'Failed to disconnect client'));
    }
  };

  const handleResetTestData = async () => {
    setResetting(true);
    try {
      const result = await routeServiceApi.resetTestData();
      const summary = result as ResetTestDataResult | undefined;
      if (summary) {
        message.success(
          `Reset complete: ${summary.createdUsers} users rebuilt, ${summary.onlineUsers} online, ${summary.deletedKeys} keys removed.`
        );
      } else {
        message.success('Test data reset successfully.');
      }
      await loadClients();
    } catch (resetError) {
      message.error(getErrorMessage(resetError, 'Failed to reset test data'));
    } finally {
      setResetting(false);
    }
  };

  const handleRegister = async (values: { userName: string }) => {
    setRegistering(true);
    try {
      const user = await routeServiceApi.register({ userName: values.userName.trim() });
      setRegisteredUser(user ?? null);
      setRegisterVisible(false);
      registerForm.resetFields();
      message.success(`Client "${values.userName}" registered successfully.`);
      await loadClients();
    } catch (registerError) {
      message.error(getErrorMessage(registerError, 'Failed to register client'));
    } finally {
      setRegistering(false);
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
      width: 120,
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
    },
    {
      title: 'Assigned Broker',
      dataIndex: 'brokerName',
      key: 'brokerName',
      width: 180,
      render: (brokerName: string) =>
        brokerName ? <Tag color="blue">{brokerName}</Tag> : <Text type="secondary">Unassigned</Text>,
    },
    {
      title: 'Broker Address',
      key: 'brokerAddress',
      render: (_, record) => (
        <Text type="secondary">{formatBrokerAddress(record.brokerIp, record.brokerTcpPort, record.brokerHttpPort)}</Text>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 180,
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="View details">
            <Button type="link" size="small" icon={<InfoCircleOutlined />} onClick={() => showDetail(record)}>
              Detail
            </Button>
          </Tooltip>
          {record.online && (
            <Popconfirm
              title={`Disconnect "${record.userName}"?`}
              description="This clears the current broker assignment and marks the user offline."
              onConfirm={() => void handleDisconnect(record)}
              okText="Disconnect"
              cancelText="Cancel"
            >
              <Button type="link" size="small" danger icon={<DisconnectOutlined />}>
                Kick
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: '0 8px' }}>
      {error && (
        <Alert
          message="Failed to load client data"
          description={error}
          type="error"
          showIcon
          closable
          onClose={() => setError(null)}
          style={{ marginBottom: 16 }}
        />
      )}

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic title="Registered Clients" value={clients.length} prefix={<UserOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic title="Online" value={onlineCount} valueStyle={{ color: '#3f8600' }} prefix={<WifiOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic title="Offline" value={offlineCount} valueStyle={{ color: '#fa8c16' }} prefix={<DisconnectOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic title="Assigned Broker" value={assignedCount} valueStyle={{ color: '#1677ff' }} />
          </Card>
        </Col>
      </Row>

      <Card>
        <Row justify="space-between" align="middle" gutter={[12, 12]}>
          <Col flex="auto">
            <Space wrap>
              <Text strong style={{ fontSize: 16 }}>
                <UserOutlined style={{ marginRight: 8 }} />
                MQTT Client Registry
              </Text>
              <Tag color="blue">{filteredClients.length} visible</Tag>
            </Space>
          </Col>
          <Col>
            <Space wrap>
              <Input
                placeholder="Search by user, broker, or id"
                prefix={<SearchOutlined />}
                allowClear
                value={searchText}
                onChange={(event) => setSearchText(event.target.value)}
                style={{ width: 260 }}
              />
              <Button icon={<PlusOutlined />} type="primary" onClick={() => setRegisterVisible(true)}>
                Register Client
              </Button>
              <Popconfirm
                title="Reset all demo data?"
                description="This rebuilds the default 10-user test dataset and clears the current user registry."
                onConfirm={() => void handleResetTestData()}
                okText="Reset"
                cancelText="Cancel"
                okButtonProps={{ danger: true }}
              >
                <Button icon={<ExperimentOutlined />} loading={resetting}>
                  Reset Test Data
                </Button>
              </Popconfirm>
              <Button icon={<ReloadOutlined />} onClick={() => void loadClients()} loading={loading}>
                Refresh
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

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
            showSizeChanger: true,
          }}
          scroll={{ x: 980 }}
          locale={{
            emptyText: 'No registered clients found. Create one with "Register Client" or rebuild the test dataset.',
          }}
        />
      </Card>

      <Modal
        title="Register a Client"
        open={registerVisible}
        onCancel={() => setRegisterVisible(false)}
        onOk={() => registerForm.submit()}
        okText="Register"
        confirmLoading={registering}
        destroyOnClose
      >
        <Form form={registerForm} layout="vertical" onFinish={handleRegister}>
          <Form.Item
            label="User Name"
            name="userName"
            rules={[
              { required: true, message: 'Please enter a client user name' },
              { min: 3, message: 'Use at least 3 characters' },
            ]}
          >
            <Input placeholder="sensor-01" maxLength={64} />
          </Form.Item>
          <Alert
            type="info"
            showIcon
            message="The route service only needs a user name for registration. It returns the generated token when the user is created for the first time."
          />
        </Form>
      </Modal>

      <Modal
        title="Client Details"
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        footer={[
          <Button key="close" onClick={() => setDetailVisible(false)}>
            Close
          </Button>,
          selectedClient?.online ? (
            <Button
              key="disconnect"
              danger
              icon={<DisconnectOutlined />}
              onClick={() => selectedClient && void handleDisconnect(selectedClient)}
            >
              Disconnect
            </Button>
          ) : null,
        ]}
        width={640}
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
              {selectedClient.online ? <Tag color="success">Online</Tag> : <Tag>Offline</Tag>}
            </Descriptions.Item>
            <Descriptions.Item label="Assigned Broker">
              {selectedClient.brokerName ? <Tag color="blue">{selectedClient.brokerName}</Tag> : 'Unassigned'}
            </Descriptions.Item>
            <Descriptions.Item label="Broker Address">
              {formatBrokerAddress(
                selectedClient.brokerIp,
                selectedClient.brokerTcpPort,
                selectedClient.brokerHttpPort
              )}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      <Modal
        title="Registration Result"
        open={Boolean(registeredUser)}
        onCancel={() => setRegisteredUser(null)}
        footer={[
          <Button key="done" type="primary" onClick={() => setRegisteredUser(null)}>
            Done
          </Button>,
        ]}
      >
        {registeredUser && (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="User ID">
              <Text code>{registeredUser.userId}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="User Name">
              <Text code>{registeredUser.userName}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Token">
              {registeredUser.token ? (
                <Text code copyable>
                  {registeredUser.token}
                </Text>
              ) : (
                <Text type="secondary">
                  Existing user detected. The backend did not issue a new token in this response.
                </Text>
              )}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default Clients;

