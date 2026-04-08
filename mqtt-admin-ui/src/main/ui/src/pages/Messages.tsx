import React, { useEffect, useState } from 'react';
import { Table, Card, Tag, Button, Space, Modal, Descriptions, Typography, message } from 'antd';
import { ReloadOutlined, EyeOutlined } from '@ant-design/icons';
import { adminApi, MessageHistory } from '../api';

const { Title } = Typography;

const Messages: React.FC = () => {
  const [messages, setMessages] = useState<MessageHistory[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
  const [selectedMessage, setSelectedMessage] = useState<MessageHistory | null>(null);
  const [modalVisible, setModalVisible] = useState(false);

  const fetchMessages = async (page: number = 1, size: number = 20) => {
    setLoading(true);
    try {
      const response = await adminApi.getMessages(page, size);
      if (response.data.success) {
        setMessages(response.data.data || []);
        setPagination({
          ...pagination,
          current: response.data.page || page,
          total: response.data.total || 0,
        });
      } else {
        message.error(response.data.message || '获取消息列表失败');
      }
    } catch (error) {
      message.error('获取消息列表失败');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMessages();
  }, []);

  const handleTableChange = (paginationConfig: any) => {
    fetchMessages(paginationConfig.current, paginationConfig.pageSize);
  };

  const handleRefresh = () => {
    fetchMessages(pagination.current, pagination.pageSize);
  };

  const handleViewDetail = (record: MessageHistory) => {
    setSelectedMessage(record);
    setModalVisible(true);
  };

  const getQosColor = (qos: number) => {
    switch (qos) {
      case 0: return 'green';
      case 1: return 'orange';
      case 2: return 'red';
      default: return 'default';
    }
  };

  const formatTime = (timestamp: number) => {
    return new Date(timestamp).toLocaleString();
  };

  const columns = [
    {
      title: '消息ID',
      dataIndex: 'msgUUID',
      key: 'msgUUID',
      width: 200,
      ellipsis: true,
    },
    {
      title: '主题',
      dataIndex: 'topic',
      key: 'topic',
      width: 200,
      ellipsis: true,
    },
    {
      title: 'QoS',
      dataIndex: 'qos',
      key: 'qos',
      width: 80,
      render: (qos: number) => (
        <Tag color={getQosColor(qos)}>QoS {qos}</Tag>
      ),
    },
    {
      title: '发布�?,
      dataIndex: 'publisherId',
      key: 'publisherId',
      width: 150,
      ellipsis: true,
    },
    {
      title: '保留消息',
      dataIndex: 'retained',
      key: 'retained',
      width: 100,
      render: (retained: boolean) => (
        <Tag color={retained ? 'blue' : 'default'}>
          {retained ? '�? : '�?}
        </Tag>
      ),
    },
    {
      title: '时间',
      dataIndex: 'timestamp',
      key: 'timestamp',
      width: 180,
      render: (timestamp: number) => formatTime(timestamp),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_: any, record: MessageHistory) => (
        <Space>
          <Button
            type="link"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record)}
          >
            详情
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Card>
      <Card.Meta
        title={
          <Space style={{ display: 'flex', justifyContent: 'space-between' }}>
            <Title level={4} style={{ margin: 0 }}>消息管理</Title>
            <Button icon={<ReloadOutlined />} onClick={handleRefresh}>
              刷新
            </Button>
          </Space>
        }
      />
      <Table
        columns={columns}
        dataSource={messages}
        rowKey="msgUUID"
        loading={loading}
        pagination={{
          ...pagination,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `�?${total} 条`,
        }}
        onChange={handleTableChange}
        style={{ marginTop: 16 }}
      />

      <Modal
        title="消息详情"
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setModalVisible(false)}>
            关闭
          </Button>,
        ]}
        width={700}
      >
        {selectedMessage && (
          <Descriptions column={2} bordered>
            <Descriptions.Item label="消息ID" span={2}>
              {selectedMessage.msgUUID}
            </Descriptions.Item>
            <Descriptions.Item label="主题" span={2}>
              {selectedMessage.topic}
            </Descriptions.Item>
            <Descriptions.Item label="QoS">
              <Tag color={getQosColor(selectedMessage.qos)}>
                QoS {selectedMessage.qos}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="保留消息">
              <Tag color={selectedMessage.retained ? 'blue' : 'default'}>
                {selectedMessage.retained ? '�? : '�?}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="发布�?>
              {selectedMessage.publisherId}
            </Descriptions.Item>
            <Descriptions.Item label="时间">
              {formatTime(selectedMessage.timestamp)}
            </Descriptions.Item>
            <Descriptions.Item label="载荷" span={2}>
              <pre style={{ 
                maxHeight: '200px', 
                overflow: 'auto',
                background: '#f5f5f5',
                padding: '8px',
                borderRadius: '4px',
                margin: 0
              }}>
                {selectedMessage.payload}
              </pre>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </Card>
  );
};

export default Messages;
