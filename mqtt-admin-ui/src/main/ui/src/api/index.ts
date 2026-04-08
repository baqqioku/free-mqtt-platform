import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

export interface MessageHistory {
  msgUUID: string;
  topic: string;
  qos: number;
  payload: string;
  publisherId: string;
  timestamp: number;
  retained: boolean;
}

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  message?: string;
  total?: number;
  page?: number;
  size?: number;
}

export const adminApi = {
  getMessages: (page: number = 1, size: number = 20) => {
    return api.get<ApiResponse<MessageHistory[]>>('/admin/messages', {
      params: { page, size }
    });
  },
  
  getMessageDetail: (msgUUID: string) => {
    return api.get<ApiResponse<MessageHistory>>(`/admin/messages/${msgUUID}`);
  },
  
  getClients: () => {
    return api.get('/admin/clients');
  },
};

export default api;
