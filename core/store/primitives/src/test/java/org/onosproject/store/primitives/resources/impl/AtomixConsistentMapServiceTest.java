/*
 * Copyright 2017-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.store.primitives.resources.impl;

import io.atomix.protocols.raft.ReadConsistency;
import io.atomix.protocols.raft.cluster.MemberId;
import io.atomix.protocols.raft.impl.RaftContext;
import io.atomix.protocols.raft.protocol.RaftServerProtocol;
import io.atomix.protocols.raft.service.ServiceId;
import io.atomix.protocols.raft.service.ServiceType;
import io.atomix.protocols.raft.service.impl.DefaultCommit;
import io.atomix.protocols.raft.service.impl.DefaultServiceContext;
import io.atomix.protocols.raft.session.RaftSession;
import io.atomix.protocols.raft.session.SessionId;
import io.atomix.protocols.raft.session.impl.RaftSessionContext;
import io.atomix.protocols.raft.storage.RaftStorage;
import io.atomix.protocols.raft.storage.snapshot.Snapshot;
import io.atomix.protocols.raft.storage.snapshot.SnapshotReader;
import io.atomix.protocols.raft.storage.snapshot.SnapshotStore;
import io.atomix.protocols.raft.storage.snapshot.SnapshotWriter;
import io.atomix.storage.StorageLevel;
import io.atomix.time.WallClockTimestamp;
import io.atomix.utils.concurrent.AtomixThreadFactory;
import io.atomix.utils.concurrent.SingleThreadContextFactory;
import org.junit.Test;
import org.onosproject.store.service.Versioned;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.mock;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.onosproject.store.primitives.resources.impl.AtomixConsistentMapOperations.GET;
import static org.onosproject.store.primitives.resources.impl.AtomixConsistentMapOperations.NEXT;
import static org.onosproject.store.primitives.resources.impl.AtomixConsistentMapOperations.OPEN_ITERATOR;
import static org.onosproject.store.primitives.resources.impl.AtomixConsistentMapOperations.PUT;
import static org.onosproject.store.primitives.resources.impl.AtomixConsistentMapOperations.Put;
import static org.onosproject.store.service.DistributedPrimitive.Type.LEADER_ELECTOR;

/**
 * Consistent map service test.
 */
public class AtomixConsistentMapServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    public void testSnapshot() throws Exception {
        SnapshotStore store = new SnapshotStore(RaftStorage.newBuilder()
                .withPrefix("test")
                .withStorageLevel(StorageLevel.MEMORY)
                .build());
        Snapshot snapshot = store.newSnapshot(2, new WallClockTimestamp());

        DefaultServiceContext context = mock(DefaultServiceContext.class);
        expect(context.serviceType()).andReturn(ServiceType.from(LEADER_ELECTOR.name())).anyTimes();
        expect(context.serviceName()).andReturn("test").anyTimes();
        expect(context.serviceId()).andReturn(ServiceId.from(1)).anyTimes();

        RaftContext server = mock(RaftContext.class);
        expect(server.getProtocol()).andReturn(mock(RaftServerProtocol.class));

        replay(context, server);

        RaftSession session = new RaftSessionContext(
            SessionId.from(1),
            MemberId.from("1"),
            "test",
            ServiceType.from(LEADER_ELECTOR.name()),
            ReadConsistency.LINEARIZABLE,
            100,
            5000,
            System.currentTimeMillis(),
            context,
            server,
            new SingleThreadContextFactory(new AtomixThreadFactory()));

        AtomixConsistentMapService service = new AtomixConsistentMapService();
        service.put(new DefaultCommit<>(
                2,
                PUT,
                new Put("foo", "Hello world!".getBytes()),
                session,
                System.currentTimeMillis()));
        service.openIterator(new DefaultCommit<>(
            3,
            OPEN_ITERATOR,
            null,
            session,
            System.currentTimeMillis()));

        try (SnapshotWriter writer = snapshot.openWriter()) {
            service.snapshot(writer);
        }

        snapshot.complete();

        service = new AtomixConsistentMapService();
        try (SnapshotReader reader = snapshot.openReader()) {
            service.install(reader);
        }

        Versioned<byte[]> value = service.get(new DefaultCommit<>(
                2,
                GET,
                new AtomixConsistentMapOperations.Get("foo"),
                mock(RaftSessionContext.class),
                System.currentTimeMillis()));
        assertNotNull(value);
        assertArrayEquals("Hello world!".getBytes(), value.value());

        assertEquals(1, service.next(new DefaultCommit<>(
            4,
            NEXT,
            new AtomixConsistentMapOperations.IteratorPosition(3L, 0),
            session,
            System.currentTimeMillis())).entries().size());
    }
}
