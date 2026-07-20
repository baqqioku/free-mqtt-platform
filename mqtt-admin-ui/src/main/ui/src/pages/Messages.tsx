import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ClearOutlined,
  MessageOutlined,
  ReloadOutlined,
  SendOutlined,
} from '@ant-design/icons';
import { adminApi, ClientInfo, getErrorMessage, MessageHistory, routeServiceApi } from '../api';
import { formatDateTime } from '../utils/format';

const { Text, Title } = Typography;

interface PublishFormValues {
  userId: number;
  topic: string;
  payload: string;
  qos: number;
  ttl?: number;
  messageId?: number;
}

interface PublishLogEntry {
  key: string;
  time: number;
  userId: number;
  userName: string;
  topic: string;
  payload: string;
  qos: number;
  ttl?: number;
  messageId?: number;
  delivery: 'dispatched' | 'queued';
  brokerName?: string;
}

const Messages: React.FC = () => {
  const [clients, setClients] = useState<ClientInfo[]>([]);
  const [serverMessages, setServerMessages] = useState<MessageHistory[]>([]);
  const [publishLog, setPublishLog] = useState<PublishLogEntry[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [sending, setSending] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [form] = Form.useForm<PublishFormValues>();

  const selectedUserId = Form.useWatch('userId', form);
  const selectedUser = clients.find((client) => client.userId === selectedUserId);

  const loadClients = async () => {
    setLoadingUsers(true);
    try {
      const data = await routeServiceApi.getClients();
      setClients(data ?? []);
    } catch (loadError) {
      message.error(getErrorMessage(loadError, 'Failed to load users'));
    } finally {
      setLoadingUsers(false);
    }
  };

  const loadServerHistory = async () => {
    setLoadingHistory(true);
    try {
      const data = await adminApi.getMessages(1, 50);
      setServerMessages(data ?? []);
      setHistoryError(null);
    } catch (loadError) {
      setHistoryError(getErrorMessage(loadError, 'Failed to load server message history'));
    } finally {
      setLoadingHistory(false);
    }
  };

  useEffect(() => {
    void loadClients();
    void loadServerHistory();
  }, []);

  const handlePublish = async (values: PublishFormValues) => {
    setSending(true);
    try {
      await routeServiceApi.pushMessage({
        userId: values.userId,
        messageId: values.messageId,
        ttl: values.ttl,
        data: {
          topic: values.topic.trim(),
          payload: values.payload,
          qos: values.qos,
        },
      });

      const delivery = selectedUser?.online ? 'dispatched' : 'queued';
      const nextEntry: PublishLogEntry = {
        key: `${Date.now()}-${values.userId}`,
        time: Date.now(),
        userId: values.userId,
        userName: selectedUser?.userName || `User ${values.userId}`,
        topic: values.topic.trim(),
        payload: values.payload,
        qos: values.qos,
        ttl: values.ttl,
        messageId: values.messageId,
        delivery,
        brokerName: selectedUser?.brokerName,
      };

      setPublishLog((current) => [nextEntry, ...current].slice(0, 20));
      message.success(
        delivery === 'dispatched'
          ? `Message sent to ${nextEntry.userName}.`
          : `User is offline. Message was accepted for queued delivery.`
      );

      form.setFieldsValue({
        ...values,
        topic: '',
        payload: '',
        messageId: undefined,
      });
    } catch (publishError) {
      message.error(getErrorMessage(publishError, 'Failed to publish message'));
    } finally {
      setSending(false);
    }
  };

  const publishColumns: ColumnsType<PublishLogEntry> = [
    {
      title: 'Time',
      dataIndex: 'time',
      key: 'time',
      width: 180,
      render: (value: number) => formatDateTime(value),
    },
    {
      title: 'User',
      key: 'user',
      width: 180,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text code>{record.userName}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            ID {record.userId}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Delivery',
      dataIndex: 'delivery',
      key: 'delivery',
      width: 130,
      render: (delivery: PublishLogEntry['delivery']) =>
        delivery === 'dispatched' ? <Tag color="success">Dispatched</Tag> : <Tag color="orange">Queued</Tag>,
    },
    {
      title: 'Topic',
      dataIndex: 'topic',
      key: 'topic',
      render: (value: string) => <Text code>{value}</Text>,
    },
    {
      title: 'QoS',
      dataIndex: 'qos',
      key: 'qos',
      width: 90,
      render: (value: number) => <Tag>QoS {value}</Tag>,
    },
    {
      title: 'Payload',
      dataIndex: 'payload',
      key: 'payload',
      ellipsis: true,
      render: (value: string) => <Text type="secondary">{value || '(empty)'}</Text>,
    },
  ];

  const serverHistoryColumns: ColumnsType<MessageHistory> = [
    {
      title: 'Message ID',
      dataIndex: 'msgUUID',
      key: 'msgUUID',
      width: 220,
      render: (value: string) => <Text code>{value || '-'}</Text>,
    },
    {
      title: 'Topic',
      dataIndex: 'topic',
      key: 'topic',
      render: (value: string) => <Tag color="geekblue">{value || '-'}</Tag>,
    },
    {
      title: 'QoS',
      dataIndex: 'qos',
      key: 'qos',
      width: 90,
      render: (value: number) => <Tag>QoS {value ?? 0}</Tag>,
    },
    {
      title: 'Publisher',
      dataIndex: 'publisherId',
      key: 'publisherId',
      width: 180,
      render: (value: string) => <Text>{value || 'system'}</Text>,
    },
    {
      title: 'Timestamp',
      dataIndex: 'timestamp',
      key: 'timestamp',
      width: 180,
      render: (value: number) => formatDateTime(value),
    },
    {
      title: 'Retained',
      dataIndex: 'retained',
      key: 'retained',
      width: 110,
      render: (value: boolean) => <Tag color={value ? 'orange' : 'default'}>{value ? 'Yes' : 'No'}</Tag>,
    },
  ];

  return (
    <div style={{ padding: '0 8px' }}>
      <Row justify="space-between" align="middle" style={{ marginBottom: 24 }}>
        <Col>
          <Space direction="vertical" size={2}>
            <Title level={3} style={{ margin: 0 }}>
              <MessageOutlined style={{ marginRight: 12 }} />
              Message Operations
            </Title>
            <Text type="secondary">
              Publish MQTT business messages and inspect the message-history integration state.
            </Text>
          </Space>
        </Col>
        <Col>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => void loadClients()} loading={loadingUsers}>
              Refresh Users
            </Button>
            <Button icon={<ReloadOutlined />} onClick={() => void loadServerHistory()} loading={loadingHistory}>
              Refresh History
            </Button>
          </Space>
        </Col>
      </Row>

      {historyError && (
        <Alert
          style={{ marginBottom: 16 }}
          type="error"
          showIcon
          message="Server history could not be loaded"
          description={historyError}
        />
      )}

      {!historyError && serverMessages.length === 0 && (
        <Alert
          style={{ marginBottom: 16 }}
          type="info"
          showIcon
          message="Server history is currently empty"
          description="The broker admin endpoint exists, but the current backend implementation returns an empty list. The frontend keeps the table visible so it will start working as soon as history storage is added."
        />
      )}

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} lg={14}>
          <Card title="Publish a Message" extra={<Tag color="blue">{clients.length} target users</Tag>}>
            <Form
              form={form}
              layout="vertical"
              initialValues={{ qos: 0, ttl: 300 }}
              onFinish={(values) => void handlePublish(values)}
            >
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item
                    label="Target User"
                    name="userId"
                    rules={[{ required: true, message: 'Please select a target user' }]}
                  >
                    <Select
                      showSearch
                      loading={loadingUsers}
                      placeholder="Select a registered user"
                      optionFilterProp="label"
                      filterOption={(input, option) =>
                        String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())
                      }
                      options={clients.map((client) => ({
                        value: client.userId,
                        label: `${client.userName} (ID:${client.userId}) ${client.online ? 'online' : 'offline'}`,
                      }))}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item
                    label="Topic"
                    name="topic"
                    rules={[{ required: true, message: 'Please enter a topic' }]}
                  >
                    <Input placeholder="device/telemetry" />
                  </Form.Item>
                </Col>
              </Row>

              <Row gutter={16}>
                <Col xs={24} md={8}>
                  <Form.Item label="QoS" name="qos">
                    <Select
                      options={[
                        { value: 0, label: 'QoS 0 - At most once' },
                        { value: 1, label: 'QoS 1 - At least once' },
                        { value: 2, label: 'QoS 2 - Exactly once' },
                      ]}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item label="TTL (seconds)" name="ttl">
                    <InputNumber min={0} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item label="Business Message ID" name="messageId">
                    <InputNumber min={0} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>

              <Form.Item label="Payload" name="payload">
                <Input.TextArea rows={6} placeholder='{"temperature": 22.5}' />
              </Form.Item>

              <Space>
                <Button type="primary" htmlType="submit" icon={<SendOutlined />} loading={sending}>
                  Publish
                </Button>
                <Button icon={<ClearOutlined />} onClick={() => form.resetFields()}>
                  Reset Form
                </Button>
              </Space>
            </Form>
          </Card>
        </Col>

        <Col xs={24} lg={10}>
          <Card title="Target Snapshot">
            {selectedUser ? (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space>
                  <Text strong code>
                    {selectedUser.userName}
                  </Text>
                  {selectedUser.online ? <Tag color="success">Online</Tag> : <Tag color="orange">Offline</Tag>}
                </Space>
                <Text type="secondary">
                  Broker: {selectedUser.brokerName ? selectedUser.brokerName : 'No active assignment'}
                </Text>
                <Alert
                  type={selectedUser.online ? 'success' : 'warning'}
                  showIcon
                  message={
                    selectedUser.online
                      ? 'The route service should forward this message directly to the current broker.'
                      : 'The route service accepts the request and relies on offline delivery storage.'
                  }
                />
              </Space>
            ) : (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="Pick a target user to preview delivery behavior."
              />
            )}
          </Card>
        </Col>
      </Row>

      <Card>
        <Tabs
          items={[
            {
              key: 'publish-log',
              label: `Session Publish Log (${publishLog.length})`,
              children: (
                <Table
                  columns={publishColumns}
                  dataSource={publishLog}
                  rowKey="key"
                  pagination={{ pageSize: 5 }}
                  locale={{
                    emptyText: 'No messages have been published from this browser session yet.',
                  }}
                />
              ),
            },
            {
              key: 'server-history',
              label: `Server History (${serverMessages.length})`,
              children: (
                <Table
                  columns={serverHistoryColumns}
                  dataSource={serverMessages}
                  rowKey={(record) => record.msgUUID || `${record.topic}-${record.timestamp}`}
                  loading={loadingHistory}
                  pagination={{ pageSize: 5 }}
                  locale={{
                    emptyText: 'The broker admin API did not return any message history records.',
                  }}
                />
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
};

export default Messages;

