export const PROJECT_SERVICES = {
  broker: {
    name: 'MQTT Broker',
    tcpPort: 23242,
    httpPort: 23240,
    websocketPort: 8083,
    sslTcpPort: 23243,
  },
  route: {
    name: 'Route Service',
    httpPort: 8084,
  },
  redis: {
    name: 'Redis',
    port: 6379,
  },
  zookeeper: {
    name: 'ZooKeeper',
    port: 2181,
  },
} as const;

export interface ApiReferenceItem {
  key: string;
  service: string;
  method: 'GET' | 'POST';
  path: string;
  description: string;
  note?: string;
}

export const API_REFERENCE: ApiReferenceItem[] = [
  {
    key: 'stats',
    service: 'Broker Admin',
    method: 'GET',
    path: '/admin/stats',
    description: 'Read dashboard metrics such as online clients, uptime, and message counters.',
  },
  {
    key: 'messages',
    service: 'Broker Admin',
    method: 'GET',
    path: '/admin/messages',
    description: 'Read server-side message history.',
    note: 'The current backend returns an empty list, so the frontend treats it as a placeholder panel.',
  },
  {
    key: 'clients',
    service: 'Route Service',
    method: 'GET',
    path: '/admin/clients',
    description: 'Read registered clients from Redis together with current broker assignment.',
  },
  {
    key: 'cluster',
    service: 'Route Service',
    method: 'GET',
    path: '/admin/cluster',
    description: 'Read broker cluster topology and active nodes.',
  },
  {
    key: 'register',
    service: 'Route Service',
    method: 'POST',
    path: '/register',
    description: 'Register a new MQTT client and receive its token.',
  },
  {
    key: 'login',
    service: 'Route Service',
    method: 'POST',
    path: '/login',
    description: 'Resolve the broker that should serve a client login request.',
  },
  {
    key: 'push',
    service: 'Route Service',
    method: 'POST',
    path: '/pushMsg',
    description: 'Push a business message to a user, or queue it for offline delivery.',
  },
  {
    key: 'disconnect',
    service: 'Route Service',
    method: 'POST',
    path: '/offerLine',
    description: 'Force a user offline and clear its broker assignment.',
  },
  {
    key: 'markDown',
    service: 'Route Service',
    method: 'GET',
    path: '/markBrokerDown',
    description: 'Temporarily blacklist a broker from the route pool.',
  },
  {
    key: 'reset',
    service: 'Route Service',
    method: 'POST',
    path: '/admin/resetTestData',
    description: 'Clear demo data and rebuild a predictable test dataset.',
  },
  {
    key: 'broker',
    service: 'Route Service',
    method: 'GET',
    path: '/getBroker',
    description: 'Read or allocate the broker for a user.',
    note: 'This endpoint may write route information when the user has no broker yet.',
  },
];

export const KNOWN_LIMITATIONS = [
  'The current broker message-history endpoint responds with an empty array, so server-side history is shown as a placeholder.',
  'There is no backend endpoint for live configuration edits yet, so the settings page is rendered as a read-only operations center.',
  'Calling /getBroker can allocate a broker for a user. The frontend avoids using it as an automatic preview to prevent side effects.',
];

export const REQUEST_EXAMPLES = {
  register: `{
  "userName": "sensor-01"
}`,
  login: `{
  "userName": "sensor-01",
  "token": "paste-the-token-from-register"
}`,
  pushMessage: `{
  "userId": 1,
  "messageId": 1001,
  "ttl": 300,
  "data": {
    "topic": "device/telemetry",
    "payload": "{\\"temperature\\": 22.5}",
    "qos": 1
  }
}`,
};

