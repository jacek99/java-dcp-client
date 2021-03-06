/*
 * Copyright (c) 2016 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package examples;

import com.couchbase.client.dcp.Client;
import com.couchbase.client.dcp.ControlEventHandler;
import com.couchbase.client.dcp.DataEventHandler;
import com.couchbase.client.dcp.config.DcpControl;
import com.couchbase.client.dcp.message.*;
import com.couchbase.client.dcp.state.PartitionState;
import com.couchbase.client.deps.io.netty.buffer.ByteBuf;
import com.couchbase.client.deps.io.netty.util.CharsetUtil;
import rx.functions.Action1;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CountBytesPerSecond {

    public static void main(String... args) throws Exception {

        final AtomicInteger numMutations = new AtomicInteger(0);
        final AtomicLong numBytes = new AtomicLong(0);
        final Client client = Client
            .configure()
            .hostnames("127.0.0.1")
            .bucket("beer-sample")
            .build();


        client.dataEventHandler(new DataEventHandler() {
            @Override
            public void onEvent(ByteBuf event) {
                // System.err.println(event);
                if (DcpMutationMessage.is(event)) {
                     System.err.println(DcpMutationMessage.toString(event));
                    numMutations.incrementAndGet();
                    numBytes.addAndGet(event.readableBytes());
                }
                client.acknowledgeBuffer(event);
                event.release();
            }
        });

        client.controlEventHandler(new ControlEventHandler() {
                @Override
                public void onEvent(ByteBuf event) {
                    if (DcpSnapshotMarkerMessage.is(event)) {
//                        System.err.println(DcpSnapshotMarkerMessage.toString(event));
                        client.acknowledgeBuffer(event);
                    } else if (DcpFailoverLogResponse.is(event)) {
  //                      System.err.println(DcpFailoverLogResponse.toString(event));
                    } else if (RollbackMessage.is(event)) {
//                        System.err.println(RollbackMessage.toString(event));
                    } else {
//                        System.err.println(MessageUtil.humanize(event));
                    }
                    event.release();
                }
            });

        client.connect().await();

        client.initializeFromBeginningToNoEnd().await();
        client.startStreams().await();
/*
       // client.stopStreams().await();

       long start = System.nanoTime();
        while(true) {
            if (numMutations.get() == 7303) {
                break;
            }
        }
        long end = System.nanoTime();

        System.err.println(TimeUnit.NANOSECONDS.toMillis(end - start));
        System.err.println("Loaded MBytes: " + numBytes.get() / 1024 / 1024);

        for (int i = 0; i < 1024; i++) {
            PartitionState ps = client.sessionState().get(i);
            System.out.println(ps);
        }

        client.disconnect().await();*/

        Thread.sleep(100000000);
    }
}
