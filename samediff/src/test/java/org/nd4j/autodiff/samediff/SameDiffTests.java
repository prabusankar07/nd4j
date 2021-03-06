package org.nd4j.autodiff.samediff;

import org.junit.Ignore;
import org.junit.Test;
import org.nd4j.autodiff.gradcheck.GradCheckUtil;
import org.nd4j.autodiff.opstate.OpExecAction;
import org.nd4j.autodiff.opstate.OpExecOrder;
import org.nd4j.autodiff.opstate.OpState;
import org.nd4j.autodiff.samediff.impl.SDVariable;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.util.DataTypeUtil;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.Op;
import org.nd4j.linalg.api.ops.impl.transforms.*;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.util.ArrayUtil;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by agibsonccc on 4/11/17.
 */
public class SameDiffTests {
    static {
        Nd4j.create(1);
        DataTypeUtil.setDTypeForContext(DataBuffer.Type.DOUBLE);
    }

    public Map<String,INDArray> variablesForInput() {
        INDArray inputs = Nd4j.create(new double[][]{
                {0.52, 1.12,  0.77},
                {0.88, -1.08, 0.15},
                {0.52, 0.06, -1.30},
                {0.74, -2.49, 1.39}
        });

        INDArray labels = Nd4j.create(new double[]{1,1,0,1}).reshape(4,1);

        INDArray weights = Nd4j.zeros(3,1);

        Map<String,INDArray> inputMap = new HashMap<>();
        inputMap.put("x",inputs);
        inputMap.put("w",weights);
        inputMap.put("y",labels);
        return inputMap;
    }


    @Test
    public void testEvalVariable() {
        SameDiff sameDiff = SameDiff.create();
        INDArray ones = Nd4j.ones(4);
        INDArray twos = ones.add(ones);
        SDVariable inputOne = sameDiff.var("inputone",ones);
        SDVariable inputResult = inputOne.add(inputOne);
        assertEquals(twos,inputResult.eval());
    }

    @Test
    public void testSigmoid() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Nd4j.linspace(1,4,4);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable sigmoid = sameDiff.sigmoid(x);
        assertEquals("sigmoid(x)",sigmoid.getVarName());
        assertEquals(2, sameDiff.graph().numVertices());
        assertEquals(1, sameDiff.graph().getEdges().size());
        assertArrayEquals(arr.shape(), sigmoid.getShape());
        assertEquals(1, sameDiff.graph().getVertexInDegree(sigmoid.getDifferentialFunction().getVertexId()));
        int[] sorted = new int[] { x.getArrayField().getVertexId(), sigmoid.getDifferentialFunction().getVertexId() };
        assertArrayEquals(sorted, sameDiff.graph().topologicalSort());
        assertEquals(1, sameDiff.graph().getOpOrder().getActions().size());
        OpState opState = sameDiff.graph().getOpOrder().getActions().get(0).getOpState();
        assertEquals("sigmoid",opState.getOpName());
        sameDiff.allocate();
        Op op = sameDiff.createOp(OpState.OpType.TRANSFORM, sameDiff.graph().getOpOrder().getActions().get(0));
        assertTrue(op instanceof Sigmoid);
        Nd4j.getExecutioner().exec(op);
        assertEquals(Transforms.sigmoid(Nd4j.linspace(1,4,4)),op.z());
    }

    @Test
    public void testSum() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1, 4, 4));
        SDVariable x = sameDiff.var("x", arr);
        SDVariable result = sameDiff.sum(x, 1);
        assertEquals("sum(x)", result.getVarName());
        assertEquals(2, sameDiff.graph().numVertices());
        assertEquals(1, sameDiff.graph().getEdges().size());
        assertArrayEquals(arr.shape(),result.getShape());
        assertArrayEquals(new int[] { 1, 2 }, sameDiff.graph().topologicalSort());
    }

    @Test
    public void testReshape() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1,4,4)).reshape(2,2);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable result = sameDiff.reshape(x, 2, 2);
        assertEquals("reshape(x)",result.getVarName());
        assertEquals(2, sameDiff.graph().numVertices());
        assertEquals(1, sameDiff.graph().getEdges().size());
        assertArrayEquals(new int[]{2,2},result.getShape());

    }

    @Test
    public void testTranspose() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1,4,4));
        SDVariable x = sameDiff.var("x",arr);
        SDVariable result = sameDiff.transpose(x);
        assertEquals("transpose(x)",result.getVarName());
        assertEquals(2, sameDiff.graph().numVertices());
        assertEquals(1, sameDiff.graph().getEdges().size());
        assertArrayEquals(new int[]{4,1},result.getShape());

    }

    @Test
    public void testDistance() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1,4,4)).reshape(2,2);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable y = sameDiff.var("y",arr);
        SDVariable result = sameDiff.cosineSimilarity(x,y,1);
        SDVariable addResult = result.add(result);

        assertEquals("cosineSimilarity(x,y)",result.getVarName());
        assertEquals(4, sameDiff.graph().numVertices());
        assertArrayEquals(new int[]{1,2},result.getShape());
    }

    @Test
    public void testTensorGradMmul() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1,4,4)).reshape(2,2);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable y = sameDiff.var("y",arr);
        SDVariable result = sameDiff.mmul(0,x,y);
        SDVariable otherResult = result.add(result);
        assertEquals("mmul(x,y)",result.getVarName());
        //3 vertices and 1 op result
        assertEquals(4, sameDiff.graph().numVertices()); // XXX: Why 5 instead of 3?
        //2 edges for matrix multiply and 1 op for result
        assertEquals(3, sameDiff.graph().getEdges().size()); // XXX: Why 3 instead of 2?
        assertArrayEquals(new int[]{2,2},result.getShape());
    }


    @Test
    public void testGetInputs() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1,4,4)).reshape(2,2);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable y = sameDiff.var("y",arr);
        SDVariable result = sameDiff.mmul(0,x,y);
        SDVariable otherResult = result.add(result);
        assertEquals(2, sameDiff.graph().getInputs().size());
    }

    @Test
    public void testGetOutputs() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1,4,4)).reshape(2,2);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable y = sameDiff.var("y",arr);
        SDVariable result = sameDiff.mmul(0,x,y);
        SDVariable otherResult = result.add(result);
        assertEquals(1, sameDiff.graph().getOutputs().size());
    }

    @Test
    public void testEval() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Nd4j.linspace(1,4,4);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable sigmoid = sameDiff.sigmoid(x);
        INDArray assertion = Transforms.sigmoid(arr);
        INDArray[] eval = sameDiff.eval(Collections.singletonMap("x",arr));
        assertEquals(assertion,eval[0]);

    }

    @Test
    public void testEvalAddSelf() {
        /**
         * Note this test fails yet due to needing
         * to validate simple cases like x * x
         * matching number of inputs.
         */
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Nd4j.linspace(1,4,4);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable sigmoid = x.mul(x);
        INDArray assertion = arr.mul(arr);
        INDArray[] eval = sameDiff.eval(Collections.singletonMap("x",arr));
        assertEquals(assertion,eval[0]);

    }

    @Test
    public void testEvalAdd() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Nd4j.linspace(1,4,4);
        INDArray yArr = arr.dup();
        SDVariable x = sameDiff.var("x",arr);
        SDVariable y = sameDiff.var("y",yArr);

        SDVariable sigmoid = x.mul(y);
        INDArray assertion = arr.mul(arr);
        Map<String,INDArray> vars = new HashMap<>();
        vars.put("x",arr);
        vars.put("y",yArr);
        INDArray[] eval = sameDiff.eval(vars);
        assertEquals(assertion,eval[0]);

    }




    @Test
    public void testTensorGradTensorMmul() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1,8,8)).reshape(2,2,2);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable y = sameDiff.var("y",arr);
        SDVariable result = sameDiff.tensorMmul(x,y,new int[][]{{0},{1}},0);
        assertEquals("tensorMmul(x,y)",result.getVarName());
        assertEquals(3, sameDiff.graph().numVertices());
        assertEquals(2, sameDiff.graph().getEdges().size());
        assertArrayEquals(ArrayUtil.getTensorMmulShape(new int[]{2,2,2},new int[]{2,2,2},new int[][]{{0},{1}}),result.getShape());
        assertEquals(32, sameDiff.numElements());
    }

    @Test
    public void testDup() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1,8,8)).reshape(2,2,2);
        SDVariable x = sameDiff.var("x",arr);
        SDVariable y = sameDiff.var("y",arr);
        SameDiff tg2 = sameDiff.dup();
        assertEquals(sameDiff.graph(), tg2.graph());
    }

    @Test
    public void testOpExecutionWithAutoDiff() {
        SameDiff sameDiff = SameDiff.create();

        INDArray arr = Nd4j.linspace(1,4,4);

        SDVariable x = sameDiff.var("x", arr);
        SDVariable sigmoid = sameDiff.sigmoid(x);
        SDVariable grad = sameDiff.grad(sigmoid, x);

        List<OpExecAction> actions = sameDiff.graph().getOpOrder().getActions();

        OpState opState = actions.get(0).getOpState();
        assertEquals("sigmoid", opState.getOpName());

        OpState opState2 = actions.get(1).getOpState();
        assertEquals("sigmoidderivative", opState2.getOpName());

        sameDiff.allocate();

        Op op1 = sameDiff.createOp(actions.get(0).getOpState().getOpType(), actions.get(0));
        assertTrue(op1 instanceof Sigmoid);
        Nd4j.getExecutioner().exec(op1);
        assertEquals(Transforms.sigmoid(arr), op1.z());

        Op op2 = sameDiff.createOp(actions.get(1).getOpState().getOpType(), actions.get(1));
        assertTrue(op2 instanceof SigmoidDerivative);
        Nd4j.getExecutioner().exec(op2);
    }


    @Test
    public void testLogGrad() {
        SameDiff sameDiff = SameDiff.create();
        SDVariable input = sameDiff.var("x",Nd4j.linspace(1,4,4));
        SDVariable log = sameDiff.log(input);
        SDVariable grad = sameDiff.grad(log,input);
        INDArray gradResult = sameDiff.execAndEndResult();
        INDArray assertion = Nd4j.create(new double[]{1,0.5,0.33,0.25});
        assertTrue(assertion.equalsWithEps(gradResult,1e-2));
    }


    @Test
    public void testElementWiseDivAndRDiv() {
        SameDiff sameDiff = SameDiff.create();
        INDArray ones = Nd4j.ones(4);
        INDArray toDivBy = Nd4j.valueArrayOf(4,0.25);
        Map<String,INDArray> xAndY = new HashMap<>();
        xAndY.put("x",ones);
        xAndY.put("y",toDivBy);
        sameDiff.defineFunction("div", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable x = sameDiff.var("x",inputs.get("x"));
                SDVariable y = sameDiff.var("y",inputs.get("y"));
                return x.div(y);
            }
        },xAndY);

        sameDiff.defineFunction("rdiv", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable x = sameDiff.var("x",inputs.get("x"));
                SDVariable y = sameDiff.var("y",inputs.get("y"));
                return x.rdiv(y);
            }
        },xAndY);


        INDArray assertionForDiv = Nd4j.valueArrayOf(4,4.0);
        INDArray assertionForRDiv = Nd4j.valueArrayOf(4,0.25);
        assertEquals(assertionForDiv,sameDiff.getFunction("div").execAndEndResult());
        assertEquals(assertionForRDiv,sameDiff.getFunction("rdiv").execAndEndResult());

    }


    @Test
    public void testNegativeGradient() {
        SameDiff sameDiff = SameDiff.create();
        INDArray ones = Nd4j.ones(4);
        Map<String,INDArray> xAndY = new HashMap<>();
        xAndY.put("x",ones);
        sameDiff.defineFunction("neg", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable x = sameDiff.var("x",inputs.get("x"));
                return sameDiff.neg(x);
            }
        },xAndY);

        INDArray assertionForDiv = Nd4j.valueArrayOf(4,-1);
        assertEquals(assertionForDiv,sameDiff.getFunction("neg").execAndEndResult());

    }


    @Test
    public void testGradCheck() {
        SameDiff sameDiff = SameDiff.create();
        Map<String,INDArray> inputs = variablesForInput();
        SDVariable x = sameDiff.var("x",inputs.get("x"));
        SDVariable y = sameDiff.var("y",inputs.get("y"));
        SDVariable w = sameDiff.var("w",inputs.get("w"));

        SDVariable learningRate = sameDiff.scalar("lr",0.01);

        SDVariable preOutput = sameDiff.mmul(0,x,w);

        SDVariable outputs = sameDiff.sigmoid(preOutput);
        List<Op> ops = sameDiff.exec();
        assertEquals(2,ops.size());
        assertEquals("mmul",ops.get(0).name());
        assertEquals("sigmoid",ops.get(1).name());
        assertEquals(6,sameDiff.graph().numVertices());
        assertEquals(3,sameDiff.graph().getEdges().size());
        //    label_probabilities = preds * targets + (1 - preds) * (1 - targets)
        SDVariable outputTimesY = outputs.mul(y);
        SDVariable oneMinusOutput = outputs.rsub(1.0);
        SDVariable probs = outputTimesY.add(oneMinusOutput.mul(y.rsub(1.0)));
        SDVariable logProbs = sameDiff.log(probs);
        SDVariable sum = sameDiff.sum(logProbs,Integer.MAX_VALUE);
        //ensure the output is scalar shape
        assertEquals(1,ArrayUtil.prod(sum.getShape()));
        SDVariable negSum = sameDiff.neg(sum);
        GradCheckUtil.checkGradients(negSum,y,1e-3,1e-3,true,inputs);
    }



    @Test
    public void testSumOp() {
        SameDiff sameDiff = SameDiff.create();
        INDArray sumInput = Nd4j.linspace(1,4,4).reshape(2,2);
        Map<String,INDArray> inputs = new HashMap<>();
        inputs.put("x",sumInput);
        sameDiff.defineFunction("sum", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable input = sameDiff.var("x",inputs.get("x"));
                SDVariable sum = sameDiff.sum(input,1);
                return sum;
            }
        },inputs);

        INDArray assertion = sumInput.sum(1);
        INDArray executions = sameDiff.execAndEndResult("sum");
        assertEquals(assertion,executions);
    }

    @Test
    public void testSumGradient() {
        SameDiff sameDiff = SameDiff.create();
        INDArray sumInput = Nd4j.linspace(1,4,4).reshape(2,2);
        Map<String,INDArray> inputs = new HashMap<>();
        inputs.put("x",sumInput);
        sameDiff.defineFunction("sumWithGradient", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable input = sameDiff.var("x",inputs.get("x"));
                SDVariable sum = sameDiff.sum(input,1);
                //original shape ends up being 2,2
                SDVariable grad = sameDiff.grad(sum,sameDiff.var("grad",Nd4j.valueArrayOf(4,1.0).reshape(2,2)));
                return grad;
            }
        },inputs);

        INDArray executions = sameDiff.execAndEndResult("sumWithGradient");
        assertArrayEquals(sumInput.shape(),executions.shape());
        assertEquals(Nd4j.ones(2,2),executions);
    }


    @Test
    public void testNestedExecution() {
        SameDiff outer = SameDiff.create();
        Map<String,INDArray> input = new HashMap<>();
        input.put("x",Nd4j.ones(2));
        outer.defineFunction("firstadd", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable input = sameDiff.var("x",inputs.get("x"));
                SDVariable ret = input.add(input);
                return ret;
            }
        },input);

        outer.defineFunction("secondadd", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable result = outer.invokeFunctionOn("firstadd",sameDiff);
                SDVariable one = sameDiff.scalar("scalar",1.0);
                return result.add(one);
            }
        });

        SameDiff secondAdd = outer.getSameDiffFunctionInstances().get("secondadd");
        INDArray[] outputs = secondAdd.eval(input);
        INDArray outputsAssertion = Nd4j.valueArrayOf(2,2.0);
        assertEquals(outputsAssertion,outputs[0]);
    }


    @Test
    public void testResultPropagation() {
        SameDiff sameDiff = SameDiff.create();
        INDArray inputs = Nd4j.create(new double[][]{
                {0.52, 1.12,  0.77},
                {0.88, -1.08, 0.15},
                {0.52, 0.06, -1.30},
                {0.74, -2.49, 1.39}
        });


        INDArray weights = Nd4j.randn(3,1);

        SDVariable x = sameDiff.var("x",inputs);
        SDVariable w = sameDiff.var("w",weights);
        SDVariable preOutput = sameDiff.mmul(0,x,w);

        SDVariable outputs = sameDiff.sigmoid(preOutput);
        List<Op> ops = sameDiff.exec();
        assertTrue(ops.get(0).z() == ops.get(1).x());

    }

    @Test
    public void testSimpleDefineFunction() {
        SameDiff sameDiffOuter = SameDiff.create();
        Map<String,INDArray> inputs = variablesForInput();
        inputs.remove("y");
        String logisticForward = "logisticPredictions";
        sameDiffOuter.defineFunction(logisticForward, (sameDiff, inputs1) -> {
            SDVariable input = sameDiff.var("x", inputs1.get("x"));
            SDVariable w = sameDiff.var("w", inputs1.get("w"));
            SDVariable preOutput = sameDiff.mmul(0,input,w);
            SDVariable sigmoid = sameDiff.sigmoid(preOutput);
            return sigmoid;
        },inputs);

        assertEquals(1,sameDiffOuter.definedFunctionNames().size());

        //note here that we don't add the duplicate ops with define function anymore
    }


    @Test
    public void testSoftmax() {
        SameDiff sameDiff = SameDiff.create();
        INDArray sumInput = Nd4j.linspace(1,4,4).reshape(2,2);
        Map<String,INDArray> inputs = new HashMap<>();
        inputs.put("x",sumInput);
        sameDiff.defineFunction("softmax", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable input = sameDiff.var("x",inputs.get("x").dup());
                SDVariable softmax = sameDiff.softmax(input);
                //original shape ends up being 2,2
                return softmax;
            }
        },inputs);

        INDArray assertions = Transforms.softmax(sumInput);
        INDArray executions = sameDiff.execAndEndResult("softmax");
        assertArrayEquals(sumInput.shape(),executions.shape());
        System.out.println(executions);
        assertEquals(assertions,executions);
    }

    @Test
    public void testSoftmaxGradient() {
        SameDiff sameDiff = SameDiff.create();
        INDArray sumInput = Nd4j.linspace(1,4,4).reshape(2,2);
        Map<String,INDArray> inputs = new HashMap<>();
        inputs.put("x",sumInput);
        sameDiff.defineFunction("softmaxGradient", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable input = sameDiff.var("x",inputs.get("x"));
                SDVariable softmax = sameDiff.softmax(input);
                SDVariable sum = sameDiff.sum(softmax,Integer.MAX_VALUE);
                //original shape ends up being 2,2
                SDVariable grad = sum.backward();
                return grad;
            }
        },inputs);

        INDArray executions = sameDiff.execAndEndResult("softmaxGradient");
        INDArray assertion = Nd4j.getExecutioner().execAndReturn(new SoftMaxDerivative(sumInput));
        assertArrayEquals(sumInput.shape(),executions.shape());
        assertEquals(assertion,executions);
        System.out.println(executions);
        //assertEquals(Nd4j.ones(2,2),executions);
    }



    @Test
    public void testExpGradient() {
        SameDiff sameDiff = SameDiff.create();
        INDArray sumInput = Nd4j.linspace(1,4,4).reshape(2,2);
        Map<String,INDArray> inputs = new HashMap<>();
        inputs.put("x",sumInput);
        sameDiff.defineFunction("expGradient", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable input = sameDiff.var("x",inputs.get("x"));
                SDVariable softmax = sameDiff.exp(input);
                //original shape ends up being 2,2
                SDVariable grad = sameDiff.grad(softmax,sameDiff.var("grad",sumInput.dup()));
                return grad;
            }
        },inputs);

        INDArray executions = sameDiff.execAndEndResult("expGradient");
        INDArray assertion = Nd4j.getExecutioner().execAndReturn(new Exp(sumInput));
        assertArrayEquals(sumInput.shape(),executions.shape());
        assertEquals(assertion,executions);
        System.out.println(executions);
        //assertEquals(Nd4j.ones(2,2),executions);
    }


    @Test
    public void testTanhGradient() {
        SameDiff sameDiff = SameDiff.create();
        INDArray sumInput = Nd4j.linspace(1,4,4).reshape(2,2);
        Map<String,INDArray> inputs = new HashMap<>();
        inputs.put("x",sumInput);
        sameDiff.defineFunction("tanhGradient", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable input = sameDiff.var("x",inputs.get("x"));
                SDVariable tanh = sameDiff.tanh(input);
                //original shape ends up being 2,2
                SDVariable grad = sameDiff.grad(tanh,sameDiff.var("grad",sumInput.dup()));
                return grad;
            }
        },inputs);

        INDArray executions = sameDiff.execAndEndResult("tanhGradient");
        INDArray assertion = Nd4j.getExecutioner().execAndReturn(new TanhDerivative(sumInput));
        assertArrayEquals(sumInput.shape(),executions.shape());
        assertEquals(assertion,executions);
        System.out.println(executions);
        //assertEquals(Nd4j.ones(2,2),executions);
    }



    @Test
    public void testRsubScalar() {
        SameDiff sameDiff = SameDiff.create();
        Map<String,INDArray> params = new HashMap<>();
        INDArray var = Nd4j.valueArrayOf(4,2);
        params.put("x",var);
        sameDiff.defineFunction("rsubop", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable input = sameDiff.var("x",inputs.get("x"));
                SDVariable ret = input.rsub(1.0);
                return ret;
            }
        },params);

        SameDiff logisticGraph = sameDiff.getSameDiffFunctionInstances().get("rsubop");
        INDArray[] outputs = logisticGraph.eval(params);
        assertEquals(Nd4j.ones(4).muli(-1),outputs[0]);
        System.out.println(Arrays.toString(outputs));



    }

    @Test
    public void testFunctionScalarResultPropagation() {
        SameDiff sameDiffOuter = SameDiff.create();
        Map<String,INDArray> inputs = variablesForInput();

        sameDiffOuter.defineFunction("logisticPredictions", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable input = sameDiff.var("x",inputs.get("x"));
                SDVariable w = sameDiff.var("w",inputs.get("w"));
                SDVariable preOutput = sameDiff.mmul(0,input,w);
                SDVariable sigmoid = sameDiff.sigmoid(preOutput);
                return sigmoid;
            }
        },inputs);

        sameDiffOuter.defineFunction("oneminuspredictions", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable y = sameDiff.var("y",inputs.get("y"));
                SDVariable oneMinusPredictions = y.rsub(1.0);
                return oneMinusPredictions;
            }
        },inputs);


        SameDiff logisticGraph = sameDiffOuter.getFunction("oneminuspredictions");
        INDArray[] outputs = logisticGraph.eval(inputs);
        INDArray assertion = Nd4j.create(new double[]{0,0,1,0});
        assertEquals(assertion,outputs[outputs.length - 1]);

    }

    @Test
    public void testInplaceSubi() {
        SameDiff sameDiffOuter = SameDiff.create();
        Map<String,INDArray> params = new HashMap<>();
        params.put("x",Nd4j.ones(4));
        sameDiffOuter.defineFunction("inplacesubi", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable inplace = sameDiff.var("x",inputs.get("x"));
                return inplace.subi(1.0);
            }
        },params);

        sameDiffOuter.getFunction("inplacesubi").eval(params);
        assertEquals(Nd4j.zeros(4),params.get("x"));
    }


    @Test
    public void testMmul() {
        SameDiff sameDiffOuter = SameDiff.create();
        Map<String,INDArray> inputs = variablesForInput();
        SDVariable x = sameDiffOuter.var("x",inputs.get("x"));
        SDVariable w = sameDiffOuter.var("w",inputs.get("w"));
        SDVariable output = sameDiffOuter.mmul(0,x,w);
        assertEquals(1,sameDiffOuter.graph().getOpOrder().getActions().size());
    }

    @Test
    public void testTransformPostExecFunction() {
        SameDiff sameDiffOuter = SameDiff.create();
        Map<String,INDArray> inputs = variablesForInput();

        sameDiffOuter.defineFunction("logisticPredictions", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable input = sameDiff.var("x",inputs.get("x"));
                SDVariable w = sameDiff.var("w",inputs.get("w"));
                SDVariable preOutput = sameDiff.mmul(0,input,w);
                SDVariable sigmoid = sameDiff.sigmoid(preOutput);
                return sigmoid;
            }
        },inputs);

        sameDiffOuter.defineFunction("loss", (sameDiff, inputs1) -> {
            SDVariable outputs = sameDiffOuter.invokeFunctionOn("logisticPredictions",sameDiff);
            return outputs;
        },inputs);


        SameDiff logisticGraph = sameDiffOuter.getFunction("loss");
        assertEquals(2,logisticGraph.graph().getOpOrder().getActions().size());


    }


    @Test
    public void testGraphBuildingWithScalars() {
        SameDiff sameDiffOuter = SameDiff.create();
        Map<String,INDArray> inputs = variablesForInput();

        sameDiffOuter.defineFunction("logisticPredictions",new LogisticPredictions(),inputs);

        SameDiff logisticPrediction = sameDiffOuter.getFunction("logisticPredictions");
        List<String> logisticOpNameAssertions = Arrays.asList("mmul","sigmoid");
        //do standalone test before new op definition to verify graph references
        //aren't changed with new instances
        OpExecOrder logisticPredictionOrder = logisticPrediction.graph().getOpOrder();
        for(int i = 0; i < 2; i++) {
            assertEquals(logisticOpNameAssertions.get(i),logisticPredictionOrder.getActions().get(i).getOpState().getOpName());
        }


        sameDiffOuter.defineFunction("loss", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable outputs = sameDiffOuter.invokeFunctionOn("logisticPredictions",sameDiff);
                SDVariable outputTimesY = outputs.rsub(1.0);
                return outputTimesY;
            }
        },inputs);


        logisticPredictionOrder = logisticPrediction.graph().getOpOrder();
        for(int i = 0; i < 2; i++) {
            assertEquals(logisticOpNameAssertions.get(i),logisticPredictionOrder.getActions().get(i).getOpState().getOpName());
        }

        SameDiff logisticGraph = sameDiffOuter.getFunction("loss");
        List<String> opNameAssertions = Arrays.asList("mmul","sigmoid","rsub_scalar");
        OpExecOrder opExecOrder = logisticGraph.graph().getOpOrder();
        System.out.println(opExecOrder);
        assertEquals(3,opExecOrder.getActions().size());
        for(int i = 0; i < 3; i++) {
            assertEquals(opNameAssertions.get(i),opExecOrder.getActions().get(i).getOpState().getOpName());
        }

    }


    @Test
    public void testGraphBuilding() {
        SameDiff sameDiffOuter = SameDiff.create();
        Map<String,INDArray> inputs = variablesForInput();

        sameDiffOuter.defineFunction("logisticPredictions", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable input = sameDiff.var("x",inputs.get("x"));
                SDVariable w = sameDiff.var("w",inputs.get("w"));
                SDVariable y = sameDiff.var("y",inputs.get("y"));
                SDVariable preOutput = sameDiff.mmul(0,input,w);
                SDVariable sigmoid = sameDiff.sigmoid(preOutput);

                return sigmoid;
            }
        },inputs);

        sameDiffOuter.defineFunction("loss", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable outputs = sameDiffOuter.invokeFunctionOn("logisticPredictions",sameDiff);
                SDVariable y = sameDiff.getVariableMap().get("y");
                SDVariable outputTimesY = outputs.mul(y);
                return outputTimesY;
            }
        },inputs);


        SameDiff logisticPrediction = sameDiffOuter.getFunction("logisticPredictions");
        List<String> logisticOpNameAssertions = Arrays.asList("mmul","sigmoid");
        OpExecOrder logisticPredictionOrder = logisticPrediction.graph().getOpOrder();
        for(int i = 0; i < 2; i++) {
            assertEquals(logisticOpNameAssertions.get(i),logisticPredictionOrder.getActions().get(i).getOpState().getOpName());
        }

        SameDiff logisticGraph = sameDiffOuter.getFunction("loss");
        List<String> opNameAssertions = Arrays.asList("mmul","sigmoid","mul");
        OpExecOrder opExecOrder = logisticGraph.graph().getOpOrder();
        assertEquals(3,opExecOrder.getActions().size());
        int[] topoOrder = logisticGraph.graph().topologicalSort();
        for(int i = 0; i < 3; i++) {
            assertEquals(opNameAssertions.get(i),opExecOrder.getActions().get(i).getOpState().getOpName());
        }

    }

    @Test
    public void testLogisticTestOutput() {
        SameDiff sameDiffOuter = SameDiff.create();
        Map<String,INDArray> inputs = variablesForInput();

        sameDiffOuter.defineFunction("logisticPredictions", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable input = sameDiff.var("x",inputs.get("x"));
                SDVariable w = sameDiff.var("w",inputs.get("w"));
                SDVariable y = sameDiff.var("y",inputs.get("y"));
                SDVariable preOutput = sameDiff.mmul(0,input,w);
                SDVariable sigmoid = sameDiff.sigmoid(preOutput);
                return sigmoid;
            }
        },inputs);

        sameDiffOuter.defineFunction("loss", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable outputs = sameDiffOuter.invokeFunctionOn("logisticPredictions",sameDiff);
                SDVariable y = sameDiff.getVariableMap().get("y");
                SDVariable oneMinusOutput = outputs.rsub(1.0);
                SDVariable oneMinusPredictions = y.rsub(1.0);
                SDVariable outputTimesY = outputs.mul(y);
                SDVariable oneMinusMul = oneMinusOutput.mul(oneMinusPredictions);
                SDVariable probs = outputTimesY.add(oneMinusMul);
                SDVariable logProbs = sameDiff.log(probs);
                SDVariable sum = sameDiff.sum(logProbs,Integer.MAX_VALUE);
                SDVariable negSum = sameDiff.neg(sum);
                return negSum;
            }
        },inputs);



        sameDiffOuter.defineFunction("lossGrad", new SameDiff.SameDiffFunctionDefinition() {
            @Override
            public SDVariable define(SameDiff sameDiff, Map<String, INDArray> inputs) {
                SDVariable outputs = sameDiffOuter.invokeFunctionOn("loss",sameDiff);
                SDVariable wrtWeights = sameDiff.var("w",inputs.get("w"));
                SDVariable y2 = sameDiff.var("y2",inputs.get("y"));
                SDVariable grad = sameDiff.grad(outputs,y2);
                return grad;
            }
        },inputs);

        

    }

    @Test
    public void testLogisticRegression() throws Exception {
        SameDiff sameDiff = SameDiff.create();
        INDArray inputs = Nd4j.create(new double[][]{
                {0.52, 1.12,  0.77},
                {0.88, -1.08, 0.15},
                {0.52, 0.06, -1.30},
                {0.74, -2.49, 1.39}
        });

        INDArray labels = Nd4j.create(new double[]{1,1,0,0}).reshape(4,1);

        INDArray weights = Nd4j.zeros(3,1);

        SDVariable x = sameDiff.var("x",inputs);
        SDVariable y = sameDiff.var("y",labels);
        SDVariable w = sameDiff.var("w",weights);

        SDVariable learningRate = sameDiff.scalar("lr",0.01);

        SDVariable preOutput = sameDiff.mmul(0,x,w);

        SDVariable outputs = sameDiff.sigmoid(preOutput);
        List<Op> ops = sameDiff.exec();
        assertEquals(2,ops.size());
        assertEquals("mmul",ops.get(0).name());
        assertEquals("sigmoid",ops.get(1).name());
        assertEquals(6,sameDiff.graph().numVertices());
        assertEquals(3,sameDiff.graph().getEdges().size());
        //    label_probabilities = preds * targets + (1 - preds) * (1 - targets)
        SDVariable outputTimesY = outputs.mul(y);
        SDVariable oneMinusOutput = outputs.rsub(sameDiff.scalar("one",1.0));
        SDVariable probs = outputTimesY.add(oneMinusOutput.mul(y.rsub(sameDiff.scalar("onetwo",1.0))));
        SDVariable logProbs = sameDiff.log(probs);
        SDVariable sum = sameDiff.sum(logProbs,Integer.MAX_VALUE);
        //ensure the output is scalar shape
        assertEquals(1,ArrayUtil.prod(sum.getShape()));
        SDVariable negSum = sameDiff.neg(sum);
       /* SDVariable outputGrad = sameDiff.grad(negSum,w);
        assertArrayEquals(new int[]{3,1},outputGrad.getShape());
        SDVariable preUpdate = w.mul(outputGrad);
        SDVariable update = preUpdate.mul(learningRate);
        SDVariable inPlaceUpdate = w.subi(update);

        System.out.println(sameDiff.graph().numVertices() + " and " + sameDiff.graph().getEdges().size());
        ops = sameDiff.exec();
        for(int i = 0; i < 10; i++) {
            INDArray output =  w.getArr();
            INDArray score = sameDiff.execAndEndResult(ops);
            System .out.println("Update " + output + " with score " + score);
        }

        System.out.println(ops);*/
    }


    @Test
    public void testSums() {
        SameDiff sameDiff = SameDiff.create();
        INDArray ones = Nd4j.ones(4);
        SDVariable sdVariable = sameDiff.var("ones",ones);
        SDVariable scalarOne = sameDiff.var("add1",Nd4j.scalar(1.0));
        SDVariable result = sdVariable.addi(scalarOne);
        SDVariable total = sameDiff.sum(result,Integer.MAX_VALUE);
        List<Op> ops = sameDiff.exec();
        INDArray output = null;
        for(int i = 0; i < 5; i++) {
            output = sameDiff.execAndEndResult(ops);
            System.out.println("Ones " + ones);
            System.out.println(output);
        }

        assertEquals(Nd4j.valueArrayOf(4,7),ones);
        assertEquals(28,output.getDouble(0),1e-1);
    }

}

