import React from 'react';
import { Card, Row, Col, Statistic } from 'antd';
import { 
  CloudServerOutlined, 
  MessageOutlined, 
  RiseOutlined,
  ClockCircleOutlined 
} from '@ant-design/icons';

const Dashboard: React.FC = () => {
  return (
    <div>
      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic
              title="在线客户�?
              value={0}
              prefix={<CloudServerOutlined />}
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="今日消息�?
              value={0}
              prefix={<MessageOutlined />}
              valueStyle={{ color: '#cf1322' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="消息吞吐�?
              value={0}
              suffix="�?�?
              prefix={<RiseOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="运行时长"
              value={0}
              suffix="小时"
              prefix={<ClockCircleOutlined />}
            />
          </Card>
        </Col>
      </Row>
      <Row gutter={16} style={{ marginTop: 24 }}>
        <Col span={24}>
          <Card title="系统信息">
            <p>欢迎使用 MQTT 管理平台</p>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Dashboard;
