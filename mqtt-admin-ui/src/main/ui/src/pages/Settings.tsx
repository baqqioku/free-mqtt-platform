import React from 'react';
import { Card, Form, Input, Button, Switch, Space } from 'antd';

const Settings: React.FC = () => {
  const [form] = Form.useForm();

  const onFinish = (values: any) => {
    console.log('Settings saved:', values);
  };

  return (
    <Card title="系统设置">
      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        initialValues={{
          serverPort: 1883,
          maxMessageSize: 1048576,
          enableAuth: false,
        }}
      >
        <Form.Item label="MQTT服务器端�? name="serverPort">
          <Input type="number" />
        </Form.Item>
        
        <Form.Item label="最大消息大�? name="maxMessageSize">
          <Input type="number" suffix="字节" />
        </Form.Item>
        
        <Form.Item 
          label="启用认证" 
          name="enableAuth" 
          valuePropName="checked"
        >
          <Switch />
        </Form.Item>
        
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">
              保存设置
            </Button>
            <Button onClick={() => form.resetFields()}>
              重置
            </Button>
          </Space>
        </Form.Item>
      </Form>
    </Card>
  );
};

export default Settings;
