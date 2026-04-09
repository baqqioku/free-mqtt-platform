import React, { useState, useEffect } from 'react';
import {
  Card,
  Form,
  Input,
  Switch,
  Button,
  Space,
  Row,
  Col,
  Typography,
  Divider,
  InputNumber,
  Select,
  message,
  Tag,
  Alert,
  Tabs,
  Table,
  Modal,
  Descriptions
} from 'antd';
import {
  SettingOutlined,
  SaveOutlined,
  ReloadOutlined,
  DatabaseOutlined,
  CloudServerOutlined,
  SafetyCertificateOutlined,
  BellOutlined,
  ApiOutlined,
  ApartmentOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined
} from '@ant-design/icons';

const { Text, Title, Paragraph } = Typography;
const { Option } = Select;
const { TextArea } = Input;

const Settings: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  const [activeTab, setActiveTab] = useState('server');

  // Mock system config - would come from API in production
  const [config, setConfig] = useState({
    // Server Settings
    serverPort: 23242,
    wsPort: 8083,
    httpApiPort: 23240,
    maxConnections: 10000,
    keepAliveInterval: 60,
    willDelaySeconds: 0,

    // Cluster Settings
    clusterEnabled: false,
    zkAddress: '127.0.0.1:2181',
    brokerName: 'broker-1',

    // Auth Settings
    authEnabled: true,
    aclEnabled: true,
    aclDefaultAllow: true,
    
    // Redis Settings
    redisHost: 'localhost',
    redisPort: 6379,
    redisPassword: '',
    
    // Route Service
    routeServiceUrl: 'http://localhost:8084',
  });

  useEffect(() => {
    form.setFieldsValue(config);
  }, [config, form]);

  const handleSave = async (section: string) => {
    setSaving(true);
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 800));
      message.success(`${section} settings saved successfully!`);
    } catch (err) {
      message.error('Failed to save settings');
    } finally {
      setSaving(false);
    }
  };

  const handleReset = () => {
    Modal.confirm({
      title: 'Reset All Settings',
      content: 'Are you sure you want to reset all settings to defaults?',
      okText: 'Yes, Reset',
      cancelText: 'Cancel',
      okType: 'danger',
      onOk: () => {
        setConfig({ ...config });
        form.resetFields();
        message.info('Settings reset to default values');
      }
    });
  };

  const handleTestConnection = async (type: string) => {
    setLoading(true);
    try {
      await new Promise(resolve => setTimeout(resolve, 1000));
      message.success(`${type} connection test successful`);
    } catch (err) {
      message.error(`Failed to connect to ${type}`);
    } finally {
      setLoading(false);
    }
  };

  // System Status Data
  const systemStatusData = [
    { key: '1', component: 'MQTT Broker', status: 'Running', port: 23242 },
    { key: '2', component: 'WebSocket', status: 'Running', port: 8083 },
    { key: '3', component: 'HTTP API', status: 'Running', port: 23240 },
    { key: '4', component: 'Route Service', status: 'Running', port: 8084 },
    { key: '5', component: 'ZooKeeper', status: 'Connected', port: 2181 },
    { key: '6', component: 'Redis', status: 'Connected', port: 6379 },
  ];

  const statusColumns = [
    {
      title: 'Component',
      dataIndex: 'component',
      key: 'component',
      render: (text: string) => <Text strong>{text}</Text>
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) =>
        ['Running', 'Connected'].includes(status) ? (
          <Tag icon={<CheckCircleOutlined />} color="success">{status}</Tag>
        ) : (
          <Tag icon={<ExclamationCircleOutlined />} color="error">{status}</Tag>
        )
    },
    {
      title: 'Port',
      dataIndex: 'port',
      key: 'port',
      render: (port: number) => <Text code>{port}</Text>
    }
  ];

  const tabItems = [
    {
      key: 'server',
      label: <><CloudServerOutlined /> Server Config</>,
      children: (
        <Form layout="vertical" form={form} size="large">
          <Row gutter={24}>
            <Col span={12}>
              <Form.Item label="MQTT TCP Port" name="serverPort">
                <InputNumber min={1024} max={65535} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="WebSocket Port" name="wsPort">
                <InputNumber min={1024} max={65535} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          
          <Row gutter={24}>
            <Col span={12}>
              <Form.Item label="HTTP API Port" name="httpApiPort">
                <InputNumber min={1024} max={65535} style={{ width: '100%' }} disabled />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="Max Connections" name="maxConnections">
                <InputNumber min={100} max={100000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={24}>
            <Col span={12}>
              <Form.Item label="Keep Alive Interval (seconds)" name="keepAliveInterval">
                <InputNumber min={10} max={3600} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="Will Delay (seconds)" name="willDelaySeconds">
                <InputNumber min={0} max={3600} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Divider />

          <Space>
            <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={() => handleSave('Server')}>
              Save Changes
            </Button>
            <Button icon={<ReloadOutlined />} onClick={() => form.resetFields()}>
              Reset Form
            </Button>
          </Space>
        </Form>
      )
    },
    {
      key: 'cluster',
      label: <><ApartmentOutlined /> Cluster</>,
      children: (
        <Form layout="vertical" size="large">
          <Alert
            message="Cluster Configuration"
            description="Enable cluster mode for high availability and horizontal scaling. Requires ZooKeeper."
            type="info"
            showIcon
            style={{ marginBottom: 24 }}
          />

          <Form.Item label="Enable Cluster Mode" valuePropName="checked">
            <Switch checkedChildren="ON" unCheckedChildren="OFF" />
          </Form.Item>

          <Row gutter={24}>
            <Col span={16}>
              <Form.Item label="ZooKeeper Address">
                <Input placeholder="host:port,host2:port2..." defaultValue={config.zkAddress} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="Broker Name">
                <Input placeholder="broker-id" defaultValue={config.brokerName} />
              </Form.Item>
            </Col>
          </Row>

          <Divider />
          
          <Space>
            <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={() => handleSave('Cluster')}>
              Save Cluster Config
            </Button>
            <Button 
              icon={<ApiOutlined />}
              loading={loading}
              onClick={() => handleTestConnection('ZooKeeper')}
            >
              Test ZK Connection
            </Button>
          </Space>
        </Form>
      )
    },
    {
      key: 'auth',
      label: <><SafetyCertificateOutlined /> Authentication</>,
      children: (
        <Form layout="vertical" size="large">
          <Alert
            message="Security Settings"
            description="Configure authentication and access control for MQTT clients."
            type="warning"
            showIcon
            style={{ marginBottom: 24 }}
          />

          <Card size="small" title={<><BellOutlined /> General</>} style={{ marginBottom: 16 }}>
            <Row gutter={24}>
              <Col span={12}>
                <Form.Item label="Enable Authentication" valuePropName="checked">
                  <Switch checkedChildren="ON" unCheckedChildren="OFF" defaultChecked={config.authEnabled} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="Default Allow" valuePropName="checked">
                  <Switch checkedChildren="ON" unCheckedChildren="OFF" defaultChecked={config.aclDefaultAllow} />
                </Form.Item>
              </Col>
            </Row>
          </Card>

          <Card size="small" title={<><SafetyCertificateOutlined /> ACL Configuration</>}>
            <Form.Item label="Enable ACL" valuePropName="checked">
              <Switch checkedChildren="ON" unCheckedChildren="OFF" defaultChecked={config.aclEnabled} />
            </Form.Item>
            
            <Form.Item label="ACL Rules File Path">
              <Input placeholder="/path/to/acl.conf" defaultValue="./acl.conf" />
            </Form.Item>
            
            <Form.Item label="ACL Reload Interval (seconds)">
              <InputNumber min={0} max={86400} style={{ width: '100%' }} defaultValue={30} />
            </Form.Item>
          </Card>

          <Divider />
          
          <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={() => handleSave('Auth')}>
            Save Auth Settings
          </Button>
        </Form>
      )
    },
    {
      key: 'storage',
      label: <><DatabaseOutlined /> Storage</>,
      children: (
        <Form layout="vertical" size="large">
          <Alert
            message="Redis Configuration"
            description="Configure Redis connection for session storage, retained messages, and offline message queue."
            type="info"
            showIcon
            style={{ marginBottom: 24 }}
          />

          <Row gutter={24}>
            <Col span={12}>
              <Form.Item label="Redis Host">
                <Input placeholder="localhost or IP address" defaultValue={config.redisHost} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="Redis Port">
                <InputNumber min={1} max={65535} style={{ width: '100%' }} defaultValue={config.redisPort} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item label="Password (optional)">
            <Input.Password placeholder="Leave empty if no authentication required" defaultValue={config.redisPassword} />
          </Form.Item>

          <Form.Item label="Database Number">
            <InputNumber min={0} max={15} style={{ width: '100%' }} defaultValue={0} />
          </Form.Item>

          <Divider />
          
          <Space>
            <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={() => handleSave('Storage')}>
              Save Storage Config
            </Button>
            <Button
              icon={<DatabaseOutlined />}
              loading={loading}
              onClick={() => handleTestConnection('Redis')}
            >
              Test Connection
            </Button>
          </Space>
        </Form>
      )
    },
    {
      key: 'route',
      label: <><ApiOutlined /> Route Service</>,
      children: (
        <Form layout="vertical" size="large">
          <Alert
            message="Route Service Integration"
            description="Configure the route service URL for client routing and load balancing."
            type="info"
            showIcon
            style={{ marginBottom: 24 }}
          />

          <Form.Item label="Route Service Base URL">
            <Input placeholder="http://route-service:8084" defaultValue={config.routeServiceUrl} addonBefore="URL" />
          </Form.Item>

          <Descriptions bordered column={1} size="small" style={{ marginBottom: 16 }}>
            <Descriptions.Item label="Available Endpoints">
              <div>
                <Text code>/register</Text> - User registration<br/>
                <Text code>/login</Text> - User login<br/>
                <Text code>/pushMsg</Text> - Push message<br/>
                <Text code>/getBroker</Text> - Get broker info<br/>
                <Text code>/markBrokerDown</Text> - Mark broker down
              </div>
            </Descriptions.Item>
          </Descriptions>

          <Divider />
          
          <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={() => handleSave('Route')}>
            Save Route Config
          </Button>
        </Form>
      )
    },
    {
      key: 'system',
      label: <><SettingOutlined /> System Status</>,
      children: (
        <div>
          <Alert
            message="System Components Status"
            description="Current status of all system components and services."
            type="success"
            showIcon
            style={{ marginBottom: 24 }}
          />

          <Table
            columns={statusColumns}
            dataSource={systemStatusData}
            pagination={false}
            size="middle"
            bordered
          />

          <Divider />
          
          <Space wrap>
            <Button danger onClick={handleReset} icon={<ReloadOutlined />}>
              Reset to Defaults
            </Button>
          </Space>
        </div>
      )
    }
  ];

  return (
    <div style={{ padding: '0 8px' }}>
      {/* Page Header */}
      <Card>
        <Row justify="space-between" align="middle">
          <Col>
            <Title level={4} style={{ margin: 0 }}>
              <SettingOutlined style={{ marginRight: 12 }} />
              System Settings & Configuration
            </Title>
          </Col>
        </Row>
      </Card>

      {/* Settings Tabs */}
      <Card style={{ marginTop: 16 }}>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={tabItems}
          type="card"
          size="large"
        />
      </Card>
    </div>
  );
};

export default Settings;
