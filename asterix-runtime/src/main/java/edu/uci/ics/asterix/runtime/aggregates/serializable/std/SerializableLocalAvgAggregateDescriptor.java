package edu.uci.ics.asterix.runtime.aggregates.serializable.std;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.uci.ics.asterix.common.config.GlobalConfig;
import edu.uci.ics.asterix.common.functions.FunctionConstants;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.ADoubleSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AFloatSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AInt16SerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AInt32SerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AInt64SerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AInt8SerializerDeserializer;
import edu.uci.ics.asterix.formats.nontagged.AqlSerializerDeserializerProvider;
import edu.uci.ics.asterix.om.base.ADouble;
import edu.uci.ics.asterix.om.base.AInt64;
import edu.uci.ics.asterix.om.base.AMutableDouble;
import edu.uci.ics.asterix.om.base.AMutableInt64;
import edu.uci.ics.asterix.om.base.ANull;
import edu.uci.ics.asterix.om.functions.IFunctionDescriptor;
import edu.uci.ics.asterix.om.functions.IFunctionDescriptorFactory;
import edu.uci.ics.asterix.om.types.ARecordType;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.om.types.AUnionType;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.asterix.om.types.EnumDeserializer;
import edu.uci.ics.asterix.om.types.IAType;
import edu.uci.ics.asterix.runtime.aggregates.base.AbstractSerializableAggregateFunctionDynamicDescriptor;
import edu.uci.ics.asterix.runtime.evaluators.common.AccessibleByteArrayEval;
import edu.uci.ics.asterix.runtime.evaluators.common.ClosedRecordConstructorEvalFactory.ClosedRecordConstructorEval;
import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.common.exceptions.NotImplementedException;
import edu.uci.ics.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyEvaluator;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyEvaluatorFactory;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopySerializableAggregateFunction;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopySerializableAggregateFunctionFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.data.std.util.ArrayBackedValueStorage;
import edu.uci.ics.hyracks.data.std.util.ByteArrayAccessibleOutputStream;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IFrameTupleReference;

public class SerializableLocalAvgAggregateDescriptor extends AbstractSerializableAggregateFunctionDynamicDescriptor {

    private static final long serialVersionUID = 1L;
    public final static FunctionIdentifier FID = new FunctionIdentifier(FunctionConstants.ASTERIX_NS,
            "local-avg-serial", 1);
    public static final IFunctionDescriptorFactory FACTORY = new IFunctionDescriptorFactory() {
        public IFunctionDescriptor createFunctionDescriptor() {
            return new SerializableLocalAvgAggregateDescriptor();
        }
    };

    @Override
    public FunctionIdentifier getIdentifier() {
        return FID;
    }

    @Override
    public ICopySerializableAggregateFunctionFactory createSerializableAggregateFunctionFactory(ICopyEvaluatorFactory[] args)
            throws AlgebricksException {
        final ICopyEvaluatorFactory[] evals = args;
        List<IAType> unionList = new ArrayList<IAType>();
        unionList.add(BuiltinType.ANULL);
        unionList.add(BuiltinType.ADOUBLE);
        final ARecordType recType = new ARecordType(null, new String[] { "sum", "count" }, new IAType[] {
                new AUnionType(unionList, "OptionalDouble"), BuiltinType.AINT64 }, true);

        return new ICopySerializableAggregateFunctionFactory() {
            private static final long serialVersionUID = 1L;

            public ICopySerializableAggregateFunction createAggregateFunction() throws AlgebricksException {
                return new ICopySerializableAggregateFunction() {
                    private ArrayBackedValueStorage inputVal = new ArrayBackedValueStorage();
                    private ICopyEvaluator eval = evals[0].createEvaluator(inputVal);
                    private ClosedRecordConstructorEval recordEval;

                    private AMutableDouble aDouble = new AMutableDouble(0);
                    private AMutableInt64 aInt64 = new AMutableInt64(0);

                    @SuppressWarnings("unchecked")
                    private ISerializerDeserializer<ADouble> doubleSerde = AqlSerializerDeserializerProvider.INSTANCE
                            .getSerializerDeserializer(BuiltinType.ADOUBLE);
                    @SuppressWarnings("unchecked")
                    private ISerializerDeserializer<AInt64> int64Serde = AqlSerializerDeserializerProvider.INSTANCE
                            .getSerializerDeserializer(BuiltinType.AINT64);
                    @SuppressWarnings("unchecked")
                    private ISerializerDeserializer<ANull> nullSerde = AqlSerializerDeserializerProvider.INSTANCE
                            .getSerializerDeserializer(BuiltinType.ANULL);

                    private ArrayBackedValueStorage avgBytes = new ArrayBackedValueStorage();
                    private ByteArrayAccessibleOutputStream sumBytes = new ByteArrayAccessibleOutputStream();
                    private DataOutput sumBytesOutput = new DataOutputStream(sumBytes);
                    private ByteArrayAccessibleOutputStream countBytes = new ByteArrayAccessibleOutputStream();
                    private DataOutput countBytesOutput = new DataOutputStream(countBytes);
                    private ICopyEvaluator evalSum = new AccessibleByteArrayEval(avgBytes.getDataOutput(), sumBytes);
                    private ICopyEvaluator evalCount = new AccessibleByteArrayEval(avgBytes.getDataOutput(), countBytes);

                    @Override
                    public void init(DataOutput state) throws AlgebricksException {
                        try {
                            state.writeDouble(0.0);
                            state.writeLong(0);
                            state.writeBoolean(false);
                        } catch (IOException e) {
                            throw new AlgebricksException(e);
                        }
                    }

                    @Override
                    public void step(IFrameTupleReference tuple, byte[] state, int start, int len)
                            throws AlgebricksException {
                        inputVal.reset();
                        eval.evaluate(tuple);
                        double sum = BufferSerDeUtil.getDouble(state, start);
                        long count = BufferSerDeUtil.getLong(state, start + 8);
                        boolean metNull = BufferSerDeUtil.getBoolean(state, start + 16);
                        if (inputVal.getLength() > 0) {
                            ++count;
                            ATypeTag typeTag = EnumDeserializer.ATYPETAGDESERIALIZER
                                    .deserialize(inputVal.getByteArray()[0]);
                            switch (typeTag) {
                                case INT8: {
                                    byte val = AInt8SerializerDeserializer.getByte(inputVal.getByteArray(), 1);
                                    sum += val;
                                    break;
                                }
                                case INT16: {
                                    short val = AInt16SerializerDeserializer.getShort(inputVal.getByteArray(), 1);
                                    sum += val;
                                    break;
                                }
                                case INT32: {
                                    int val = AInt32SerializerDeserializer.getInt(inputVal.getByteArray(), 1);
                                    sum += val;
                                    break;
                                }
                                case INT64: {
                                    long val = AInt64SerializerDeserializer.getLong(inputVal.getByteArray(), 1);
                                    sum += val;
                                    break;
                                }
                                case FLOAT: {
                                    float val = AFloatSerializerDeserializer.getFloat(inputVal.getByteArray(), 1);
                                    sum += val;
                                    break;
                                }
                                case DOUBLE: {
                                    double val = ADoubleSerializerDeserializer.getDouble(inputVal.getByteArray(), 1);
                                    sum += val;
                                    break;
                                }
                                case NULL: {
                                    metNull = true;
                                    break;
                                }
                                default: {
                                    throw new NotImplementedException("Cannot compute AVG for values of type "
                                            + typeTag);
                                }
                            }
                            inputVal.reset();
                        }
                        BufferSerDeUtil.writeDouble(sum, state, start);
                        BufferSerDeUtil.writeLong(count, state, start + 8);
                        BufferSerDeUtil.writeBoolean(metNull, state, start + 16);
                    }

                    @Override
                    public void finish(byte[] state, int start, int len, DataOutput result) throws AlgebricksException {
                        double sum = BufferSerDeUtil.getDouble(state, start);
                        long count = BufferSerDeUtil.getLong(state, start + 8);
                        boolean metNull = BufferSerDeUtil.getBoolean(state, start + 16);
                        if (recordEval == null)
                            recordEval = new ClosedRecordConstructorEval(recType,
                                    new ICopyEvaluator[] { evalSum, evalCount }, avgBytes, result);
                        if (count == 0) {
                            if (GlobalConfig.DEBUG) {
                                GlobalConfig.ASTERIX_LOGGER.finest("AVG aggregate ran over empty input.");
                            }
                        } else {
                            try {
                                if (metNull) {
                                    sumBytes.reset();
                                    nullSerde.serialize(ANull.NULL, sumBytesOutput);
                                } else {
                                    sumBytes.reset();
                                    aDouble.setValue(sum);
                                    doubleSerde.serialize(aDouble, sumBytesOutput);
                                }
                                countBytes.reset();
                                aInt64.setValue(count);
                                int64Serde.serialize(aInt64, countBytesOutput);
                                recordEval.evaluate(null);
                            } catch (IOException e) {
                                throw new AlgebricksException(e);
                            }
                        }
                    }

                    @Override
                    public void finishPartial(byte[] state, int start, int len, DataOutput partialResult)
                            throws AlgebricksException {
                        finish(state, start, len, partialResult);
                    }

                };
            }
        };
    }

}