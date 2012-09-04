/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hyracks.storage.am.common.dataflow;

import java.nio.ByteBuffer;

import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.value.IRecordDescriptorProvider;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import edu.uci.ics.hyracks.dataflow.common.comm.util.FrameUtils;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.FrameTupleReference;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractUnaryInputUnaryOutputOperatorNodePushable;
import edu.uci.ics.hyracks.storage.am.common.api.IIndexAccessor;
import edu.uci.ics.hyracks.storage.am.common.api.IModificationOperationCallback;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndex;
import edu.uci.ics.hyracks.storage.am.common.api.ITupleFilter;
import edu.uci.ics.hyracks.storage.am.common.api.ITupleFilterFactory;
import edu.uci.ics.hyracks.storage.am.common.impls.NoOpOperationCallback;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.IndexOp;
import edu.uci.ics.hyracks.storage.am.common.tuples.PermutingFrameTupleReference;

public class TreeIndexInsertUpdateDeleteOperatorNodePushable extends AbstractUnaryInputUnaryOutputOperatorNodePushable {
    private final AbstractTreeIndexOperatorDescriptor opDesc;
    private final IHyracksTaskContext ctx;
    private final TreeIndexDataflowHelper treeIndexHelper;
    private FrameTupleAccessor accessor;
    private final IRecordDescriptorProvider recordDescProvider;
    private final IndexOp op;
    private final PermutingFrameTupleReference tuple = new PermutingFrameTupleReference();
    private FrameTupleReference frameTuple;
    private ByteBuffer writeBuffer;
    private IIndexAccessor indexAccessor;
    private ITupleFilter tupleFilter;
    private IModificationOperationCallback modCallback;

    public TreeIndexInsertUpdateDeleteOperatorNodePushable(AbstractTreeIndexOperatorDescriptor opDesc,
            IHyracksTaskContext ctx, int partition, int[] fieldPermutation,
            IRecordDescriptorProvider recordDescProvider, IndexOp op) {
        this.opDesc = opDesc;
        this.ctx = ctx;
        this.treeIndexHelper = (TreeIndexDataflowHelper) opDesc.getIndexDataflowHelperFactory()
                .createIndexDataflowHelper(opDesc, ctx, partition);
        this.recordDescProvider = recordDescProvider;
        this.op = op;
        tuple.setFieldPermutation(fieldPermutation);
    }

    @Override
    public void open() throws HyracksDataException {
        RecordDescriptor inputRecDesc = recordDescProvider.getInputRecordDescriptor(opDesc.getActivityId(), 0);
        accessor = new FrameTupleAccessor(ctx.getFrameSize(), inputRecDesc);
        writeBuffer = ctx.allocateFrame();
        writer.open();
        treeIndexHelper.open();
        ITreeIndex treeIndex = (ITreeIndex) treeIndexHelper.getIndexInstance();
        try {
            modCallback = opDesc.getOpCallbackProvider().getModificationOperationCallback(
                    treeIndexHelper.getResourceID());
            indexAccessor = treeIndex.createAccessor(modCallback, NoOpOperationCallback.INSTANCE);
            ITupleFilterFactory tupleFilterFactory = opDesc.getTupleFilterFactory();
            if (tupleFilterFactory != null) {
                tupleFilter = tupleFilterFactory.createTupleFilter(treeIndexHelper.ctx);
                frameTuple = new FrameTupleReference();
            }
        } catch (Exception e) {
            treeIndexHelper.close();
            throw new HyracksDataException(e);
        }
    }

    @Override
    public void nextFrame(ByteBuffer buffer) throws HyracksDataException {
        accessor.reset(buffer);
        int tupleCount = accessor.getTupleCount();
        for (int i = 0; i < tupleCount; i++) {
            try {
                if (tupleFilter != null) {
                    frameTuple.reset(accessor, i);
                    if (!tupleFilter.accept(frameTuple)) {
                        continue;
                    }
                }
                tuple.reset(accessor, i);

                switch (op) {
                    case INSERT: {
                        indexAccessor.insert(tuple);
                        break;
                    }
                    case UPDATE: {
                        indexAccessor.update(tuple);
                        break;
                    }
                    case UPSERT: {
                        indexAccessor.upsert(tuple);
                        break;
                    }
                    case DELETE: {
                        indexAccessor.delete(tuple);
                        break;
                    }
                    default: {
                        throw new HyracksDataException("Unsupported operation " + op
                                + " in tree index InsertUpdateDelete operator");
                    }
                }
            } catch (HyracksDataException e) {
                throw e;
            } catch (Exception e) {
                throw new HyracksDataException(e);
            }
        }
        // Pass a copy of the frame to next op.
        System.arraycopy(buffer.array(), 0, writeBuffer.array(), 0, buffer.capacity());
        FrameUtils.flushFrame(writeBuffer, writer);
    }

    @Override
    public void close() throws HyracksDataException {
        try {
            writer.close();
        } finally {
            treeIndexHelper.close();
        }

    }

    @Override
    public void fail() throws HyracksDataException {
        writer.fail();
    }
}