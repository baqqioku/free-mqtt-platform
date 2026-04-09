import React, { useState, useEffect, useCallback } from 'react';
import {
  Table,
  Card,
  Button,
  Space,
  Modal,
  Descriptions,
  Tag,
  Input,
  Row,
  Col,
  Typography,
  Select,
  DatePicker,
  Alert,
  Spin,
  message,
  Tooltip,
  Empty
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { RangePickerProps } from 'antd/es/date-picker';
import {
  MessageOutlined,
  SearchOutlined,
  ReloadOutlined,
  InfoCircleOutlined,
  DeleteOutlined,
  EyeOutlined,
  SendOutlined,
  ClearOutlined,
  FilterOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { adminApi, routeServiceApi, MessageHistory } from '../api';

const { Text } = Typography;
const { Option } = Select;
const { RangePicker } = DatePicker;

interface MessageRecord extends MessageHistory {
  key: string;
}

const Messages: React.FC = () => {
  const [messages, setMessages] = useState<MessageRecord[]>([]);
  const [filteredMessages, setFilteredMessages] = useState<MessageRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedMessage, setSelectedMessage] = useState<MessageRecord | null>(null);
  const [isModalVisible, setIsModalVisible] = useState(false);
  
  // Filters
  const [searchText, setSearchText] = useState('');
  const [topicFilter, setTopicFilter] = useState<string>('');
  const [qosFilter, setQosFilter] = useState<number | undefined>();
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs | null, dayjs.Dayjs | null]>([null, null]);

  // Pagination
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const [sending, setSending] = useState(false);
  const [pushModalVisible, setPushModalVisible] = useState(false);
  const [pushTopic, setPushTopic] = useState('');
  const [pushPayload, setPushPayload] = useState('');
  const [pushQos, setPushQos] = useState<number>(0);

  const fetchMessages = useCallback(async () => {
    setLoading(true);
    try {
      const res = await adminApi.getMessages(currentPage, pageSize);
      if (res?.dataBody) {
        const data = (res.dataBody as MessageHistory[]).map(m => ({
          ...m,
          key: m.msgUUID || Math.random().toString()
        }));
        setMessages(data);
      }
    } catch (err) {
      console.error('Failed to fetch messages:', err);
    } finally {
      setLoading(false);
    }
  }, [currentPage, pageSize]);

  useEffect(() => {
    fetchMessages();
  }, [fetchMessages]);

  // Apply filters
  useEffect(() => {
    let result = [...messages];

    if (searchText.trim()) {
      const lower = searchText.toLowerCase();
      result = result.filter(m =>
        m.topic?.toLowerCase().includes(lower) ||
        m.payload?.toLowerCase().includes(lower) ||
        m.publisherId?.toLowerCase().includes(lower)
      );
    }

    if (topicFilter) {
      result = result.filter(m => m.topic === topicFilter);
    }

    if (qosFilter !== undefined) {
      result = result.filter(m => m.qos === qosFilter);
    }

    if (dateRange[0]) {
      result = result.filter(m =>
        new Date(m.timestamp) >= dateRange[0].toDate()
      );
    }

    if (dateRange[1]) {
      result = result.filter(m =>
        new Date(m.timestamp) <= dateRange[1].toDate()
      );
    }

    setFilteredMessages(result);
  }, [messages, searchText, topicFilter, qosFilter, dateRange]);

  // Get unique topics for filter dropdown
  const uniqueTopics = [...new Set(messages.map(m => m.topic).filter(Boolean))];

  const showDetail = (record: MessageRecord) => {
    setSelectedMessage(record);
    setIsModalVisible(true);
  };

  const handleDelete = (msgUUID: string) => {
    Modal.confirm({
      title: 'Delete Message',
      content: 'Are you sure you want to delete this message?',
      okText: 'Yes',
      cancelText: 'No',
      onOk: () => {
        message.success('Message deleted');
        fetchMessages();
      }
    });
  };

  const handleClearAll = () => {
    Modal.confirm({
      title: 'Clear All Messages',
      content: 'Are you sure you want to clear all message history?',
      okType: 'danger',
      okText: 'Yes, Clear All',
      cancelText: 'Cancel',
      onOk: () => {
        message.info('Message clearing is not implemented in demo mode');
      }
    });
  };

  // pushMsg需要的字段
  const [pushUserId, setPushUserId] = useState<number | undefined>(undefined);
  const [userOptions, setUserOptions] = useState<{userId: number; userName: string; online: boolean; brokerName: string}[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);

  // 打开发送弹窗时加载用户列表
  const openPushModal = async () => {
    setPushModalVisible(true);
    setLoadingUsers(true);
    try {
      const res = await routeServiceApi.getClients();
      if (res?.dataBody) {
        const clients = res.dataBody as any[];
        setUserOptions(clients.map((c: any) => ({
          userId: c.userId,
          userName: c.userName,
          online: c.online,
          brokerName: c.brokerName || ''
        })));
      }
    } catch (err) {
      console.error('Failed to load users:', err);
    } finally {
      setLoadingUsers(false);
    }
  };

  const handleSendMessage = async () => {
    if (!pushUserId) {
      message.error('Please select a user');
      return;
    }
    if (!pushTopic.trim()) {
      message.error('Please enter a topic');
      return;
    }
    const selectedUser = userOptions.find(u => u.userId === pushUserId);
    if (selectedUser && !selectedUser.online) {
      message.warning('This user is offline. Message will be queued for offline delivery.');
    }
    setSending(true);
    try {
      const res = await routeServiceApi.pushMessage({
        userId: pushUserId,
        data: { topic: pushTopic.trim(), payload: pushPayload, qos: pushQos }
      });
      message.success(`Message pushed to user [${selectedUser?.userName || pushUserId}]`);
      setPushModalVisible(false);
      setPushUserId(undefined);
      setPushTopic('');
      setPushPayload('');
      setPushQos(0);
    } catch (err: any) {
      message.error(err?.response?.data?.message || err.message || 'Failed to push message');
    } finally {
      setSending(false);
    }
  };

  const columns: ColumnsType<MessageRecord> = [
    {
      title: 'Message ID',
      dataIndex: 'msgUUID',
      key: 'msgUUID',
      width: 220,
      ellipsis: true,
      render: (text: string) => (
        <Text code copyable style={{ fontSize: 12 }}>
          {text}
        </Text>
      )
    },
    {
      title: 'Topic',
      dataIndex: 'topic',
      key: 'topic',
      width: 180,
      render: (topic: string) => (
        <Tag color="geekblue" style={{ maxWidth: 170 }}>
          <MessageOutlined style={{ marginRight: 4 }} />
          {topic}
        </Tag>
      ),
      sorter: (a, b) => (a.topic || '').localeCompare(b.topic || ''),
    },
    {
      title: 'QoS',
      dataIndex: 'qos',
      key: 'qos',
      width: 70,
      align: 'center',
      filters: [
        { text: 'QoS 0', value: 0 },
        { text: 'QoS 1', value: 1 },
        { text: 'QoS 2', value: 2 }
      ],
      onFilter: (value, record) => record.qos === value,
      render: (qos: number) => (
        <Tag color={qos === 0 ? 'default' : qos === 1 ? 'blue' : 'purple'}>
          QoS {qos}
        </Tag>
      ),
    },
    {
      title: 'Publisher',
      dataIndex: 'publisherId',
      key: 'publisherId',
      width: 140,
      ellipsis: true,
      render: (id: string) => id || <Text type="secondary">System</Text>,
    },
    {
      title: 'Timestamp',
      dataIndex: 'timestamp',
      key: 'timestamp',
      width: 175,
      sorter: (a, b) => a.timestamp - b.timestamp,
      defaultSortOrder: 'descend',
      render: (timestamp: number) => timestamp > 0
        ? new Date(timestamp).toLocaleString()
        : '-'
    },
    {
      title: 'Retained',
      dataIndex: 'retained',
      key: 'retained',
      width: 90,
      align: 'center',
      filters: [
        { text: 'Yes', value: true },
        { text: 'No', value: false }
      ],
      onFilter: (value, record) => record.retained === value,
      render: (retained: boolean) => (
        <Tag color={retained ? 'orange' : 'default'}>
          {retained ? 'Retain' : 'No'}
        </Tag>
      ),
    },
    {
      title: 'Payload Preview',
      dataIndex: 'payload',
      key: 'payload',
      width: 200,
      ellipsis: true,
      render: (payload: string) => (
        payload ? (
          <Text type="secondary" style={{ maxWidth: 190 }}>
            {payload.length > 50 ? `${payload.substring(0, 50)}...` : payload}
          </Text>
        ) : <Text type="secondary" italic>No payload</Text>
      )
    },
    {
      title: 'Actions',
      key: 'action',
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="View Details">
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => showDetail(record)}
            />
          </Tooltip>
          <Tooltip title="Delete">
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={() => handleDelete(record.msgUUID)}
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: '0 8px' }}>
      {/* Header */}
      <Card>
        <Row justify="space-between" align="middle" wrap={true}>
          <Col>
            <Space size="middle" wrap>
              <Text strong style={{ fontSize: 16 }}>
                <MessageOutlined style={{ marginRight: 8 }} />
                Message History
              </Text>
              <Tag color="blue">{filteredMessages.length} Messages</Tag>
            </Space>
          </Col>
          <Col>
            <Space wrap>
              <Button
                icon={<SendOutlined />}
                type="primary"
                onClick={openPushModal}
              >
                Send Message
              </Button>
              <Button
                icon={<ClearOutlined />}
                danger
                onClick={handleClearAll}
                disabled={messages.length === 0}
              >
                Clear All
              </Button>
              <Button
                icon={<ReloadOutlined />}
                onClick={fetchMessages}
                loading={loading}
              >
                Refresh
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* Filters */}
      <Card size="small" style={{ marginTop: 16 }} title={<><FilterOutlined /> Filters</>}>
        <Row gutter={[16, 12]}>
          <Col xs={24} sm={12} md={6}>
            <Input
              placeholder="Search topic/payload..."
              prefix={<SearchOutlined />}
              allowClear
              value={searchText}
              onChange={e => setSearchText(e.target.value)}
            />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Select
              placeholder="Filter by Topic"
              allowClear
              style={{ width: '100%' }}
              onChange={setTopicFilter}
              value={topicFilter || undefined}
            >
              {uniqueTopics.map(topic => (
                <Option key={topic} value={topic}>{topic}</Option>
              ))}
            </Select>
          </Col>
          <Col xs={24} sm={12} md={4}>
            <Select
              placeholder="QoS Level"
              allowClear
              style={{ width: '100%' }}
              onChange={(v) => setQosFilter(v)}
              value={qosFilter}
            >
              <Option value={0}>QoS 0</Option>
              <Option value={1}>QoS 1</Option>
              <Option value={2}>QoS 2</Option>
            </Select>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <RangePicker
              style={{ width: '100%' }}
              onChange={(dates) => setDateRange(dates as [any, any])}
              showTime
            />
          </Col>
          <Col xs={24} sm={12} md={2}>
            <Button
              block
              onClick={() => {
                setSearchText('');
                setTopicFilter('');
                setQosFilter(undefined);
                setDateRange([null, null]);
              }}
            >
              Reset
            </Button>
          </Col>
        </Row>
      </Card>

      {/* Table */}
      <Card style={{ marginTop: 16 }}>
        <Table
          columns={columns}
          dataSource={filteredMessages}
          rowKey="key"
          loading={loading}
          scroll={{ x: 1300 }}
          size="middle"
          pagination={{
            current: currentPage,
            pageSize: pageSize,
            total: filteredMessages.length,
            showTotal: (total) => `Total ${total} messages`,
            pageSizeOptions: ['10', '20', '50'],
            showSizeChanger: true,
            onChange: (page, size) => {
              setCurrentPage(page);
              setPageSize(size);
            }
          }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  <span>
                    No messages found
                    <br />
                    <Text type="secondary">Send a message or wait for MQTT messages</Text>
                  </span>
                }
              />
            )
          }}
        />
      </Card>

      {/* Detail Modal */}
      <Modal
        title={
          <>
            <InfoCircleOutlined /> Message Details
          </>
        }
        open={isModalVisible}
        onCancel={() => setIsModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setIsModalVisible(false)}>
            Close
          </Button>
        ]}
        width={700}
      >
        {selectedMessage && (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="Message ID">
              <Text code copyable>{selectedMessage.msgUUID}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Topic">
              <Tag color="geekblue">{selectedMessage.topic}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="QoS Level">
              <Tag>{`QoS ${selectedMessage.qos}`}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Publisher ID">
              {selectedMessage.publisherId || 'System'}
            </Descriptions.Item>
            <Descriptions.Item label="Timestamp">
              {new Date(selectedMessage.timestamp).toLocaleString()}
            </Descriptions.Item>
            <Descriptions.Item label="Retained Message">
              <Tag color={selectedMessage.retained ? 'orange' : 'default'}>
                {selectedMessage.retained ? 'Yes - Retained' : 'No'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Payload">
              <pre style={{
                background: '#f5f5f5',
                padding: 12,
                borderRadius: 4,
                maxHeight: 300,
                overflow: 'auto',
                fontSize: 13,
                whiteSpace: 'pre-wrap'
              }}>
                {selectedMessage.payload || '(empty)'}
              </pre>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      {/* Send Message Modal */}
      <Modal
        title={
          <>
            <SendOutlined /> Publish New Message
          </>
        }
        open={pushModalVisible}
        onCancel={() => setPushModalVisible(false)}
        onOk={handleSendMessage}
        okText="Publish"
        cancelText="Cancel"
        confirmLoading={sending}
        width={600}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <div>
            <Text strong>Select User *</Text>
            <Select
              showSearch
              placeholder={loadingUsers ? "Loading users..." : "Select a user to push message"}
              style={{ width: '100%', marginTop: 4 }}
              value={pushUserId}
              onChange={(val) => setPushUserId(val)}
              loading={loadingUsers}
              optionFilterProp="label"
              filterOption={(input, option) =>
                (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
              options={userOptions.map(u => ({
                value: u.userId,
                label: `${u.userName} (ID: ${u.userId}) ${u.online ? '● Online' : '○ Offline'} ${u.brokerName ? '→ ' + u.brokerName : ''}`,
              }))}
            />
            {pushUserId && (() => {
              const sel = userOptions.find(u => u.userId === pushUserId);
              if (!sel) return null;
              return sel.online ? (
                <Alert type="success" message={`User "${sel.userName}" is online on ${sel.brokerName}`} style={{ marginTop: 4 }} showIcon />
              ) : (
                <Alert type="warning" message={`User "${sel.userName}" is offline. Message will be queued.`} style={{ marginTop: 4 }} showIcon />
              );
            })()}
          </div>
          <div>
            <Text strong>Topic *</Text>
            <Input
              placeholder="Enter MQTT topic (e.g., sensor/temperature)"
              value={pushTopic}
              onChange={e => setPushTopic(e.target.value)}
              style={{ marginTop: 4 }}
              prefix="# / "
            />
          </div>
          
          <div>
            <Text strong>QoS Level</Text>
            <Select
              style={{ width: '100%', marginTop: 4 }}
              value={pushQos}
              onChange={setPushQos}
            >
              <Option value={0}>QoS 0 - At most once</Option>
              <Option value={1}>QoS 1 - At least once</Option>
              <Option value={2}>QoS 2 - Exactly once</Option>
            </Select>
          </div>
          
          <div>
            <Text strong>Payload</Text>
            <Input.TextArea
              rows={5}
              placeholder="Enter message payload (JSON, text, etc.)"
              value={pushPayload}
              onChange={e => setPushPayload(e.target.value)}
              style={{ marginTop: 4 }}
            />
          </div>
        </Space>
      </Modal>
    </div>
  );
};

export default Messages;
