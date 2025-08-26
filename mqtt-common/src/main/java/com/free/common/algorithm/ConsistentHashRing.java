package com.free.common.algorithm;


import com.free.zk.core.ServerInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * 基于 MurmurHash3 的一致性哈希环实现（支持虚拟节点）
 *
 * @param <T> 代表节点对象（如：String nodeId 或自定义 ServerInfo）
 */
public class ConsistentHashRing<T> {

    // Hash 环：key = hash value (unsigned long mapped to Long), value = node
    private final NavigableMap<Long, T> ring = new TreeMap<>();

    // 用来记录每个真实节点对应的虚拟节点 hash（便于删除时清理）
    private final Map<T, List<Long>> nodeToVirtualHashes = new HashMap<>();

    // 每个真实节点的虚拟节点数量（越多分布越均匀）
    private final int virtualNodes;

    public ConsistentHashRing(int virtualNodes) {
        if (virtualNodes <= 0) throw new IllegalArgumentException("virtualNodes must > 0");
        this.virtualNodes = virtualNodes;
    }

    public ConsistentHashRing( int virtualNodes, Collection<T> nodes) {
        this.virtualNodes = virtualNodes;
        for (T node : nodes) {
            addNode(node);
        }
    }

    /**
     * 增加一个真实节点（thread-safe）
     */
    public synchronized void addNode(T node) {
        if (nodeToVirtualHashes.containsKey(node)) return; // 已存在
        List<Long> vHashes = new ArrayList<>(virtualNodes);
        for (int i = 0; i < virtualNodes; i++) {
            String vnodeKey = node.toString() + "#VN" + i; // 虚拟节点 key 表示
            long hash = MurmurHash3.hash64(vnodeKey.getBytes());
            ring.put(hash, node);
            vHashes.add(hash);
        }
        nodeToVirtualHashes.put(node, vHashes);
    }

    /**
     * 移除一个真实节点（thread-safe）
     */
    public synchronized void removeNode(T node) {
        List<Long> vHashes = nodeToVirtualHashes.remove(node);
        if (vHashes != null) {
            for (Long h : vHashes) {
                ring.remove(h);
            }
        }
    }

    /**
     * 根据 key 获取对应的节点（非阻塞读取）
     * 返回 null 表示环上没有可用节点
     */
    public T getNode(String key) {
        if (ring.isEmpty()) return null;
        long hash = MurmurHash3.hash64(key.getBytes());
        Map.Entry<Long, T> entry = ring.ceilingEntry(hash);
        if (entry == null) {
            // wrap-around
            entry = ring.firstEntry();
        }
        return entry.getValue();
    }

    /**
     * 获取环的快照（只读），用于监控/调试
     */
    public synchronized Map<Long, T> getRingSnapshot() {
        return new TreeMap<>(ring);
    }

    // ---- MurmurHash3 x64_128 (取64位输出) ----
    public static class MurmurHash3 {
        // 返回 64 位 hash（来自 x64_128 的低 64 位 XOR 高 64 位 会更分散）
        public static long hash64(byte[] data) {
            long[] h = hash128(data, 0, data.length, 0);
            // 将两个 64-bit 混合，减少碰撞（也可以直接用 h[0]）
            return h[0] ^ h[1];
        }

        // x64_128 算法，返回两个 long（低64, 高64）
        public static long[] hash128(byte[] key, int offset, int len, int seed) {
            final int nblocks = len / 16;

            long h1 = seed;
            long h2 = seed;

            final long c1 = 0x87c37b91114253d5L;
            final long c2 = 0x4cf5ad432745937fL;

            // body
            ByteBuffer buffer = ByteBuffer.wrap(key, offset, len).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < nblocks; i++) {
                long k1 = buffer.getLong();
                long k2 = buffer.getLong();

                k1 *= c1;
                k1 = Long.rotateLeft(k1, 31);
                k1 *= c2;
                h1 ^= k1;
                h1 = Long.rotateLeft(h1, 27);
                h1 += h2;
                h1 = h1 * 5 + 0x52dce729;

                k2 *= c2;
                k2 = Long.rotateLeft(k2, 33);
                k2 *= c1;
                h2 ^= k2;
                h2 = Long.rotateLeft(h2, 31);
                h2 += h1;
                h2 = h2 * 5 + 0x38495ab5;
            }

            // tail
            long k1 = 0;
            long k2 = 0;
            int tailStart = nblocks * 16;
            int remaining = len & 15;
            for (int i = remaining - 1; i >= 0; i--) {
                byte b = key[offset + tailStart + i];
                switch (i) {
                    case 15:
                        k2 ^= ((long) b) << 48;
                        break;
                    case 14:
                        k2 ^= ((long) b) << 40;
                        break;
                    case 13:
                        k2 ^= ((long) b) << 32;
                        break;
                    case 12:
                        k2 ^= ((long) b) << 24;
                        break;
                    case 11:
                        k2 ^= ((long) b) << 16;
                        break;
                    case 10:
                        k2 ^= ((long) b) << 8;
                        break;
                    case 9:
                        k2 ^= ((long) b);
                        break;
                    case 8:
                        k1 ^= ((long) b) << 56;
                        break;
                    case 7:
                        k1 ^= ((long) b) << 48;
                        break;
                    case 6:
                        k1 ^= ((long) b) << 40;
                        break;
                    case 5:
                        k1 ^= ((long) b) << 32;
                        break;
                    case 4:
                        k1 ^= ((long) b) << 24;
                        break;
                    case 3:
                        k1 ^= ((long) b) << 16;
                        break;
                    case 2:
                        k1 ^= ((long) b) << 8;
                        break;
                    case 1:
                        k1 ^= ((long) b);
                        break;
                    case 0:
                        break;
                }
            }

            if (k1 != 0) {
                k1 *= c1;
                k1 = Long.rotateLeft(k1, 31);
                k1 *= c2;
                h1 ^= k1;
            }
            if (k2 != 0) {
                k2 *= c2;
                k2 = Long.rotateLeft(k2, 33);
                k2 *= c1;
                h2 ^= k2;
            }

            // finalization
            h1 ^= len;
            h2 ^= len;

            h1 += h2;
            h2 += h1;

            h1 = fmix64(h1);
            h2 = fmix64(h2);

            h1 += h2;
            h2 += h1;

            return new long[]{h1, h2};
        }

        private static long fmix64(long k) {
            k ^= k >>> 33;
            k *= 0xff51afd7ed558ccdL;
            k ^= k >>> 33;
            k *= 0xc4ceb9fe1a85ec53L;
            k ^= k >>> 33;
            return k;
        }
    }

    // ---- 示例 / 测试 ----
    public static void main(String[] args) {
        // 一致性哈希环，每个真实节点对应 160 个虚拟节点
        ConsistentHashRing<ServerInfo> ring = new ConsistentHashRing<ServerInfo>(160);

        // 模拟集群 3 个 Broker 节点
        ServerInfo s1 = new ServerInfo("broker-1", "192.168.1.10", 1883,8081);
        ServerInfo s2 = new ServerInfo("broker-2", "192.168.1.11", 1883,8081);
        ServerInfo s3 = new ServerInfo("broker-3", "192.168.1.12", 1883,8081);

        // 添加到环
        ring.addNode(s1);
        ring.addNode(s2);
        ring.addNode(s3);

        // 模拟 5 个客户端请求，根据 clientId 找到目标 broker
        String[] clientIds = {"clientA", "clientB", "clientC", "clientD", "clientE"};

        for (String clientId : clientIds) {
            ServerInfo target = ring.getNode(clientId);
            System.out.println("clientId=" + clientId + " 分配到 -> " + target);
        }

        // 模拟 broker-2 挂掉
        System.out.println("\n--- 移除 broker-2 ---");
        ring.removeNode(s2);

        for (String clientId : clientIds) {
            ServerInfo target = ring.getNode(clientId);
            System.out.println("clientId=" + clientId + " 分配到 -> " + target);
        }

    }
}


