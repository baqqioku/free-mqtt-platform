import axios, { AxiosInstance, AxiosRequestConfig } from 'axios';

export interface ApiResponse<T> {
  code?: string;
  message?: string;
  reqNo?: string;
  dataBody?: T;
}

export interface MessageHistory {
  msgUUID: string;
  topic: string;
  qos: number;
  payload: string;
  publisherId: string;
  timestamp: number;
  retained: boolean;
}

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

export interface RegisteredUser {
  userId: number;
  userName: string;
  token?: string;
  online?: boolean;
}

export interface BrokerAssignment {
  brokerName?: string;
  ip?: string;
  tcpPort?: number;
  httpPort?: number;
  clientId?: string;
}

export interface ResetTestDataResult {
  deletedKeys: number;
  createdUsers: number;
  onlineUsers: number;
  availableBrokers: string[];
}

export interface PushMessageBody {
  topic: string;
  payload: string;
  qos: number;
}

export interface PushMessageRequest {
  userId: number;
  data: PushMessageBody;
  msgUUID?: string;
  messageId?: number;
  ttl?: number;
}

const SUCCESS_CODE = '200';

const adminClient = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

const routeClient = axios.create({
  baseURL: '/route',
  timeout: 10000,
});

function isApiResponse<T>(payload: unknown): payload is ApiResponse<T> {
  if (!payload || typeof payload !== 'object') {
    return false;
  }

  const value = payload as Record<string, unknown>;
  return 'code' in value || 'message' in value || 'dataBody' in value;
}

export function getErrorMessage(error: unknown, fallback = 'Request failed'): string {
  if (axios.isAxiosError(error)) {
    const responseData = error.response?.data;
    if (isApiResponse(responseData) && responseData.message) {
      return responseData.message;
    }

    if (typeof responseData === 'string' && responseData.trim()) {
      return responseData;
    }

    return error.message || fallback;
  }

  if (error instanceof Error) {
    return error.message || fallback;
  }

  return fallback;
}

async function request<T>(
  client: AxiosInstance,
  config: AxiosRequestConfig,
  fallbackMessage: string
): Promise<T> {
  try {
    const response = await client.request<ApiResponse<T> | T>(config);
    const payload = response.data;

    if (isApiResponse<T>(payload)) {
      if (payload.code && payload.code !== SUCCESS_CODE) {
        throw new Error(payload.message || fallbackMessage);
      }

      return payload.dataBody as T;
    }

    return payload as T;
  } catch (error) {
    throw new Error(getErrorMessage(error, fallbackMessage));
  }
}

export const adminApi = {
  getStats: () =>
    request<SystemStats>(adminClient, { url: '/admin/stats', method: 'GET' }, 'Failed to load system stats'),

  getMessages: (page = 1, size = 20) =>
    request<MessageHistory[]>(
      adminClient,
      { url: '/admin/messages', method: 'GET', params: { page, size } },
      'Failed to load message history'
    ),
};

export const routeServiceApi = {
  getClients: () =>
    request<ClientInfo[]>(routeClient, { url: '/admin/clients', method: 'GET' }, 'Failed to load clients'),

  register: (data: { userName: string }) =>
    request<RegisteredUser>(routeClient, { url: '/register', method: 'POST', data }, 'Failed to register client'),

  login: (data: { userName: string; token: string }) =>
    request<BrokerAssignment>(routeClient, { url: '/login', method: 'POST', data }, 'Failed to log in client'),

  disconnectUser: (userId: number) =>
    request<void>(
      routeClient,
      { url: '/offerLine', method: 'POST', data: { userId } },
      'Failed to disconnect client'
    ),

  getCluster: () =>
    request<ClusterInfo>(routeClient, { url: '/admin/cluster', method: 'GET' }, 'Failed to load cluster info'),

  getBroker: (userId: number) =>
    request<BrokerAssignment>(
      routeClient,
      { url: '/getBroker', method: 'GET', params: { userId } },
      'Failed to load broker assignment'
    ),

  markBrokerDown: (brokerName: string) =>
    request<void>(
      routeClient,
      { url: '/markBrokerDown', method: 'GET', params: { brokerName } },
      'Failed to mark broker down'
    ),

  pushMessage: (data: PushMessageRequest) =>
    request<void>(routeClient, { url: '/pushMsg', method: 'POST', data }, 'Failed to publish message'),

  resetTestData: () =>
    request<ResetTestDataResult>(
      routeClient,
      { url: '/admin/resetTestData', method: 'POST' },
      'Failed to reset test data'
    ),
};

