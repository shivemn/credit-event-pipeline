package com.shivemn.ccp.config;

import com.shivemn.ccp.model.CreditProfile;
import java.nio.file.Paths;
import java.util.List;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cluster.ClusterState;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Starts an embedded Ignite node and exposes the credit profile cache. */
@Configuration
public class IgniteConfig {

    public static final String PROFILE_CACHE = "creditProfiles";

    /**
     * @param persistence   when true, profiles are also written to disk under {@code workDir}, so they survive
     *                      a restart. When false, the cache lives in memory only (used by most tests).
     * @param discoveryPort first port this node uses to find other Ignite nodes. Tests use a different port,
     *                      so they never join an Ignite cluster formed by a running copy of the app.
     */
    @Bean(destroyMethod = "close")
    public Ignite ignite(
            @Value("${pipeline.ignite.instance-name:ccp-node}") String instanceName,
            @Value("${pipeline.ignite.persistence-enabled:true}") boolean persistence,
            @Value("${pipeline.ignite.discovery-port:47500}") int discoveryPort,
            @Value("${pipeline.ignite.work-dir:ignite-data}") String workDir) {
        IgniteConfiguration cfg = new IgniteConfiguration();
        cfg.setIgniteInstanceName(instanceName);
        // A fixed ID makes the on-disk data belong to this node again after a restart.
        cfg.setConsistentId(instanceName);
        cfg.setWorkDirectory(persistence
                ? Paths.get(workDir).toAbsolutePath().toString()
                : Paths.get(System.getProperty("java.io.tmpdir"), "ignite-ccp", instanceName).toString());

        // Static discovery on localhost: no multicast needed, so it works on laptops and in CI.
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(List.of("127.0.0.1:" + discoveryPort + ".." + (discoveryPort + 9)));
        TcpDiscoverySpi discovery = new TcpDiscoverySpi();
        discovery.setLocalPort(discoveryPort);
        discovery.setLocalPortRange(9);
        discovery.setIpFinder(ipFinder);
        cfg.setDiscoverySpi(discovery);

        if (persistence) {
            DataStorageConfiguration storage = new DataStorageConfiguration();
            storage.getDefaultDataRegionConfiguration().setPersistenceEnabled(true);
            // Keep the footprint laptop-sized: up to 1 GB of memory for profiles, and a small write-ahead log.
            storage.getDefaultDataRegionConfiguration().setMaxSize(1024L * 1024 * 1024);
            storage.setWalSegmentSize(16 * 1024 * 1024);
            storage.setWalSegments(4);
            storage.setMaxWalArchiveSize(128L * 1024 * 1024);
            cfg.setDataStorageConfiguration(storage);
        }

        Ignite ignite = Ignition.start(cfg);
        if (persistence) {
            // A node with persistence starts inactive until the cluster is activated.
            ignite.cluster().state(ClusterState.ACTIVE);
        }
        return ignite;
    }

    @Bean
    public IgniteCache<String, CreditProfile> profileCache(Ignite ignite) {
        CacheConfiguration<String, CreditProfile> cacheCfg = new CacheConfiguration<>(PROFILE_CACHE);
        cacheCfg.setCacheMode(CacheMode.PARTITIONED);
        cacheCfg.setAtomicityMode(CacheAtomicityMode.ATOMIC);
        cacheCfg.setBackups(1);
        // Makes the cache queryable with SQL: table CreditProfile in schema "creditProfiles".
        cacheCfg.setIndexedTypes(String.class, CreditProfile.class);
        return ignite.getOrCreateCache(cacheCfg);
    }
}
