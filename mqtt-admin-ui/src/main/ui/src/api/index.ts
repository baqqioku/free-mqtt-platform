import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

// Response interceptor for unified error handling
api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    console.error('API Error:', error);
    return Promise.reject(error);
  }
);

// ==================== 数据类型 ====================

export interface MessageHistory {
  msgUUID: string;
  topic: string;
  qos: number;
  payload: string;
  publisherId: string;
  timestamp: number;
  retained: boolean;
}

/** Route服务返回的客户端信息（从Redis读取） */
export interface ClientInfo {
  userId: number;
  userName: string;
  online: boolean;
  brokerName: string;
  brokerIp?: string;
  brokerTcpPort?: number;
  brokerHttpPort?: number;
}

export interface BrokerNode {
  brokerName: string;
  ip: string;
  tcpPort: number;
  httpPort: number;
}

export interface ClusterInfo {
  clusterName: string;
  brokers: BrokerNode[];
  brokerCount: number;
  refreshTime: number;
}

export interface SystemStats {
  totalClients: number;
  onlineClients: number;
  messagesToday: number;
  messageRate: number;
  uptime: number;
  timestamp: number;
  clusterEnabled: boolean;
}

export interface ApiResponse<T> {
  code?: string;
  message?: string;
  reqNo?: string;
  dataBody?: T;
}

// ==================== Admin API (MQTT Server 23240) ====================
// 只用 /stats，其他数据从Route服务获取
export const adminApi = {
  // 获取系统统计
  getStats: () => api.get<ApiResponse<SystemStats>>('/admin/stats'),
  // 获取消息历史
  getMessages: (page: number = 1, size: number = 20) =>
    api.get<ApiResponse<MessageHistory[]>>('/messages', { params: { page, size } }),
};

// ==================== Route API (Route Service 8084) ====================
// 客户端管理、集群管理、消息推送都走Route服务
const routeApi = axios.create({
  baseURL: '/route',
  timeout: 10000,
});

routeApi.interceptors.response.use(
  (response) => response.data,
  (error) => {
    console.error('Route API Error:', error);
    return Promise.reject(error);
  }
);

export const routeServiceApi = {
  // ---- 用户/客户端管理 ----
  // 获取所有客户端列表（从Redis）
  getClients: () => routeApi.get<ApiResponse<ClientInfo[]>>('/admin/clients'),

  // 用户注册
  register: (data: { userName: string; password: string }) =>
    routeApi.post('/register', data),

  // 用户登录
  login: (data: { userName: string; token: string }) =>
    routeApi.post('/login', data),

  // 用户下线
  offerLine: (userId: number) =>
    routeApi.post('/offerLine', { userId }),

  // ---- Broker集群管理 ----
  // 获取集群Broker列表
  getCluster: () => routeApi.get<ApiResponse<ClusterInfo>>('/admin/cluster'),

  // 获取指定用户的Broker
  getBroker: (userId: number) =>
    routeApi.get(`/getBroker?userId=${userId}`),

  // 标记Broker下线
  markBrokerDown: (brokerName: string) =>
    routeApi.get(`/markBrokerDown?brokerName=${brokerName}`),

  // ---- 消息推送 ----
  // 推送消息给指定用户（走LBS→找Broker→HTTP转发链路）
  pushMessage: (data: { userId: number; data: any; msgUUID?: string; messageId?: number }) =>
    routeApi.post('/pushMsg', data),

  // ---- 测试数据管理 ----
  // 清除所有用户数据并重新创建测试用户
  resetTestData: () => routeApi.post<ApiResponse<any>>('/admin/resetTestData'),
};

export default api;
