import React, { useEffect, useState } from 'react';
import { Table, Card, Tag, Button, Space, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { adminApi } from '../api';

const Clients: React.FC = () => {
  const [clients, setClients] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchClients = async () => {
    setLoading(true);
    try {
      const response = await adminApi.getClients();
      if (response.data.success) {
        setClients(response.data.data || []);
      } else {
        message.error(response.data.message || '获取客户端列表失�?);
      }
    } catch (error) {
      message.error('获取客户端列表失�?);
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchClients();
  }, []);

  const columns = [
    {
      title: '客户端ID',
      dataIndex: 'clientId',
      key: 'clientId',
    },
    {
      title: '状�?,
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag color={status === 'CONNECTED' ? 'green' : 'default'}>
          {status}
        </Tag>
      ),
    },
    {
      title: '连接时间',
      dataIndex: 'connectedTime',
      key: 'connectedTime',
    },
  ];

  return (
    <Card>
      <Card.Meta
        title={
          <Space style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span>客户端管�?/span>
            <Button icon={<ReloadOutlined />} onClick={fetchClients}>
              刷新
            </Button>
          </Space>
        }
      />
      <Table
        columns={columns}
        dataSource={clients}
        rowKey="clientId"
        loading={loading}
        style={{ marginTop: 16 }}
      />
    </Card>
  );
};

export default Clients;
