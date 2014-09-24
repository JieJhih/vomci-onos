package org.onlab.onos.store.impl;

import com.hazelcast.config.Config;
import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import de.javakaffee.kryoserializers.URISerializer;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Service;
import org.onlab.onos.net.DefaultDevice;
import org.onlab.onos.net.DefaultPort;
import org.onlab.onos.net.Device;
import org.onlab.onos.net.DeviceId;
import org.onlab.onos.net.Element;
import org.onlab.onos.net.MastershipRole;
import org.onlab.onos.net.Port;
import org.onlab.onos.net.PortNumber;
import org.onlab.onos.net.provider.ProviderId;
import org.onlab.onos.store.StoreService;
import org.onlab.onos.store.serializers.DefaultPortSerializer;
import org.onlab.onos.store.serializers.DeviceIdSerializer;
import org.onlab.onos.store.serializers.PortNumberSerializer;
import org.onlab.onos.store.serializers.ProviderIdSerializer;
import org.onlab.util.KryoPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Auxiliary bootstrap of distributed store.
 */
@Component(immediate = true)
@Service
public class StoreManager implements StoreService {

    private static final String HAZELCAST_XML_FILE = "etc/hazelcast.xml";

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected HazelcastInstance instance;
    private KryoPool serializerPool;


    @Activate
    public void activate() {
        try {
            Config config = new FileSystemXmlConfig(HAZELCAST_XML_FILE);
            instance = Hazelcast.newHazelcastInstance(config);
            setupKryoPool();
            log.info("Started");
        } catch (FileNotFoundException e) {
            log.error("Unable to configure Hazelcast", e);
        }
    }

    /**
     * Sets up the common serialzers pool.
     */
    protected void setupKryoPool() {
        // FIXME Slice out types used in common to separate pool/namespace.
        serializerPool = KryoPool.newBuilder()
                .register(
                        ArrayList.class,
                        HashMap.class,

                        Device.Type.class,

                        DefaultDevice.class,
                        MastershipRole.class,
                        Port.class,
                        Element.class
                )
                .register(URI.class, new URISerializer())
                .register(ProviderId.class, new ProviderIdSerializer())
                .register(DeviceId.class, new DeviceIdSerializer())
                .register(PortNumber.class, new PortNumberSerializer())
                .register(DefaultPort.class, new DefaultPortSerializer())
                .build()
                .populate(10);
    }

    @Deactivate
    public void deactivate() {
        instance.shutdown();
        log.info("Stopped");
    }

    @Override
    public HazelcastInstance getHazelcastInstance() {
        return instance;
    }


    @Override
    public byte[] serialize(final Object obj) {
        return serializerPool.serialize(obj);
    }

    @Override
    public <T> T deserialize(final byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return serializerPool.deserialize(bytes);
    }

}
