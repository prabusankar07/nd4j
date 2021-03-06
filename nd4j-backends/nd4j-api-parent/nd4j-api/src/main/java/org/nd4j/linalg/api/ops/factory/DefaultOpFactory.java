/*-
 *
 *  * Copyright 2015 Skymind,Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 *
 */

package org.nd4j.linalg.api.ops.factory;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.*;
import org.nd4j.linalg.api.ops.impl.accum.*;
import org.nd4j.linalg.api.ops.impl.accum.distances.CosineSimilarity;
import org.nd4j.linalg.api.ops.impl.accum.distances.EuclideanDistance;
import org.nd4j.linalg.api.ops.impl.accum.distances.ManhattanDistance;
import org.nd4j.linalg.api.ops.impl.broadcast.*;
import org.nd4j.linalg.api.ops.impl.indexaccum.IAMax;
import org.nd4j.linalg.api.ops.impl.indexaccum.IMax;
import org.nd4j.linalg.api.ops.impl.indexaccum.IMin;
import org.nd4j.linalg.api.ops.impl.scalar.*;
import org.nd4j.linalg.api.ops.impl.scalar.comparison.*;
import org.nd4j.linalg.api.ops.impl.shape.Broadcast;
import org.nd4j.linalg.api.ops.impl.shape.Permute;
import org.nd4j.linalg.api.ops.impl.shape.Reshape;
import org.nd4j.linalg.api.ops.impl.shape.Transpose;
import org.nd4j.linalg.api.ops.impl.transforms.*;
import org.nd4j.linalg.api.ops.impl.transforms.arithmetic.*;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * Default operations factory
 *
 * @author Adam Gibson
 */
public class DefaultOpFactory implements OpFactory {
    private Map<String, Class<? extends Op>> opClazzes;


    public DefaultOpFactory() {
        opClazzes = new HashMap<>();

        Reflections f = new Reflections(new ConfigurationBuilder().filterInputsBy(
                new FilterBuilder().include(FilterBuilder.prefix("org.nd4j")).exclude("^(?!.*\\.class$).*$") //Consider only .class files (to avoid debug messages etc. on .dlls, etc
                        .exclude("^(?!org\\.nd4j\\.linalg\\.api\\.ops).*") //Exclude any not in the ops directory
        )

                .setUrls(ClasspathHelper.forPackage("org.nd4j")).setScanners(new SubTypesScanner()));

        Set<Class<? extends Op>> clazzes = f.getSubTypesOf(Op.class);

        for (Class<? extends Op> clazz : clazzes) {
            if (Modifier.isAbstract(clazz.getModifiers()) || clazz.isInterface())
                continue;

            try {
                opClazzes.put(clazz.newInstance().name(), clazz);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     *
     * @param name
     * @param x
     * @param z
     * @return
     */
    @Override
    public Op createShape(String name, INDArray x, INDArray z) {
        switch(name) {
            case "transpose":
                return new Transpose(x,z);
            case "reshape":
                return new Reshape(x,z);
            case "permute":
                return new Permute(x,z);
            case "broadcast":
                return new Broadcast(x,z);
        }

        throw new IllegalArgumentException("Illegal name for create shape op" + name);
    }

    @Override
    public LossFunction createLossFunction(String name, INDArray x, INDArray y) {
        Class<? extends Op> clazz = opClazzes.get(name);
        try {
            Constructor<Op> constructor =
                    (Constructor<Op>) clazz.getDeclaredConstructor(INDArray.class, INDArray.class);
            Op create = constructor.newInstance(x, y);
            return (LossFunction) create;
        } catch (Exception e) {
            throw new IllegalArgumentException("Illegal op " + name);
        }

    }

    @Override
    public Accumulation createAccum(String name, INDArray x) {
        return createAccum(name,x,null,x,null);
    }

    @Override
    public Accumulation createAccum(String name, INDArray x, INDArray y, INDArray z) {
        return createAccum(name,x,y,z,null);
    }


    @Override
    public Accumulation createAccum(String name,
                                    INDArray x,
                                    INDArray y,
                                    INDArray z,
                                    Object[] extraArgs) {
        Accumulation ret = null;
        switch (name) {

            case "sum":
                ret = new Sum(x, y, z,x.length());
                break;
            case "max":
                ret = new Max(x, y, z,x.length());
                break;
            case "min":
                ret = new Min(x, y, z,x.length());
                break;
            case "norm1":
                ret = new Norm1(x, y,z, x.length());
                break;
            case "norm2":
                ret = new Norm2(x, y,z, x.length());
                break;
            case "prod":
                ret = new Prod(x, y,z, x.length());
                break;
            case "std":
                ret = new StandardDeviation(x, y,z, x.length(),(boolean) extraArgs[0]);
                break;
            case "var":
                ret = new Variance(x, y,z, x.length(),(boolean) extraArgs[0]);
                break;
            case "euclidean":
                ret = new EuclideanDistance(x, y,z, x.length());
                break;
            case "cosine":
            case "cosinesimilarity":
                ret = new CosineSimilarity(x, y,z, x.length());
                break;
            case "manhattan":
                ret = new ManhattanDistance(x, y,z, x.length());
                break;
            case "mmul":
                ret = new Mmul(x, y,z, x.length());
                break;
            case "tensorMmul":
                ret = new TensorMmul(x, y,z,(int[][]) extraArgs[0]);
                break;


        }

        if(ret == null)
            throw new IllegalArgumentException("Illegal operation name " + name);

        ret.setExtraArgs(extraArgs);
        return ret;
    }


    @Override
    public Accumulation createAccum(String name, INDArray x, INDArray y) {
        return createAccum(name,x,y,x,null);
    }

    /**
     *
     * @param opName
     * @param x
     * @param y
     *@param z
     * @param extraArgs   @return
     */
    @Override
    public IndexAccumulation createIndexAccum(String opName, INDArray x, INDArray y, INDArray z, Object[] extraArgs) {
        IndexAccumulation ret = null;
        switch (opName) {
            case "iamax":
                ret = new IAMax(x,y);
                break;
            case "imax":
                ret = new IMax(x,y);
                break;
            case "imin":
                ret = new IMin(x,y);
                break;
        }

        ret.setExtraArgs(extraArgs);
        return ret;
    }

    @Override
    public IndexAccumulation createIndexAccum(String name, INDArray x) {
        return createIndexAccum(name,x,null , x, null);
    }

    @Override
    public IndexAccumulation createIndexAccum(String name, INDArray x, INDArray y) {
        return createIndexAccum(name,x,y,x,null);
    }

    @Override
    public TransformOp createTransform(String name, INDArray x, INDArray y) {
        return createTransform(name,x,y,x,null);

    }

    @Override
    public TransformOp createTransform(String name, INDArray x) {
        return createTransform(name,x,null,x,null);
    }

    @Override
    public TransformOp createTransform(String name, INDArray x, Object[] extraArgs) {
        return createTransform(name,x,null,x,extraArgs);
    }


    @Override
    public TransformOp createTransform(String name, INDArray x, INDArray y, INDArray z) {
        return createTransform(name,x,y,z,null);
    }

    /**
     * @param name
     * @param x
     * @param y
     * @param z
     * @param extraArgs
     * @return
     */
    @Override
    public TransformOp createTransform(String name,
                                       INDArray x,
                                       INDArray y,
                                       INDArray z,
                                       Object[] extraArgs) {
        TransformOp op = null;
        switch (name) {
            case "set":
                op = new org.nd4j.linalg.api.ops.impl.transforms.Set(x,y,z,z.length());
                break;
            case "relu":
                op = new RectifedLinear(x, z, x.length(),extraArgs == null || extraArgs[0] == null ? 0.0 : (double) extraArgs[0]);
                break;
            case "step":
                op = new Step(x,y,z,x.length(),extraArgs == null || extraArgs[0] == null  ? 0.0 : (double) extraArgs[0]);
                break;
            case "abs":
                op = new Abs(x, z);
                break;
            case "acos":
                op = new ACos(x, z);
                break;
            case "asin":
                op = new ASin(x, z);
                break;
            case "atan":
                op = new ATan(x, z);
                break;
            case "ceil":
                op = new Ceil(x, z);
                break;
            case "cos":
                op = new Cos(x, z);
                break;
            case "exp":
                op = new Exp(x, z);
                break;
            case "elu":
                op = new ELU(x, z);
                break;
            case "floor":
                op = new Floor(x, z);
                break;
            case "hardtanh":
                op = new HardTanh(x, z);
                break;
            case "hardsigmoid":
                op = new HardSigmoid(x, z);
                break;
            case "identity":
                op = new Identity(x, z);
                break;
            case "leakyrelu":
                op = new LeakyReLU(x, z);
                break;
            case "log":
                op = new Log(x, z);
                break;
            case "logsoftmax":
                op = new LogSoftMax(x, z);
                break;
            case "maxout":
                op = new MaxOut(x, z);
                break;
            case "negative":
                op = new Negative(x, z);
                break;
            case "pow":
                op = new Pow(x, z, (double) extraArgs[0]);
                break;
            case "round":
                op = new Round(x, z);
                break;
            case "sigmoid":
                op = new Sigmoid(x, z);
                break;
            case "sign":
                op = new Sign(x, z);
                break;
            case "sin":
                op = new Sin(x, z);
                break;
            case "softsign":
                op = new SoftSign(x, z);
                break;
            case "sqrt":
                op = new Sqrt(x, z);
                break;
            case "stabilize":
                op = new Stabilize(x, z, 1);
                break;
            case "tanh":
                op = new Tanh(x, z);
                break;
            case "rationaltanh":
                op = new RationalTanh(x, z);
                break;
            case "timesoneminus":
                op = new TimesOneMinus(x, z);
                break;
            case "softmaxderivative":
                op = new SoftMaxDerivative(x, z);
                break;
            case "softmax":
                op = new SoftMax(x, z);
                break;
            case "softplus":
                op = new SoftPlus(x, z);
                break;
            case "cube":
                op = new Cube(x, z);
                break;
            case "sigmoidderivative":
                op = new SigmoidDerivative(x,z);
                break;
            case "hard_sigmoidderivative":
                op = new HardSigmoidDerivative(x,z);
                break;
            case "hardtanhderivative":
                op = new HardTanhDerivative(x,z);
                break;
            case "tanhderivative":
                op = new TanhDerivative(x,z);
                break;
            case "leakyreluderivative":
                op = new LeakyReLUDerivative(x,z);
                break;
            case "mul":
                op = new MulOp(x,y,z);
                break;
            case "add":
                op = new AddOp(x,y,z);
                break;
            case "sub":
                op = new SubOp(x,y,z);
                break;
            case "div":
                op = new DivOp(x,y,z);
                break;
            case "rdiv":
                op = new RDivOp(x,y,z);
                break;
            case "rsub":
                op = new RSubOp(x,y,z);
                break;
            case "neg":
                op = new Negative(x,z);
                break;
            default:
                throw new ND4JIllegalStateException("No op found " + name);
        }



        op.setExtraArgs(extraArgs);
        return op;
    }

    /**
     * @param name
     * @param x
     * @param y
     * @param scalar
     * @return
     */
    @Override
    public ScalarOp createScalarTransform(String name, INDArray x, INDArray y, double scalar) {
        return createScalarTransform(name,x,y,x,null,scalar);
    }

    /**
     * @param name
     * @param x
     * @param scalar
     * @return
     */
    @Override
    public ScalarOp createScalarTransform(String name, INDArray x, double scalar) {
        return createScalarTransform(name,x,null,x,null,scalar);
    }

    /**
     * @param name
     * @param x
     * @param extraArgs
     * @param scalar
     * @return
     */
    @Override
    public ScalarOp createScalarTransform(String name,
                                          INDArray x,
                                          Object[] extraArgs,
                                          double scalar) {
        return createScalarTransform(name,x,null,x,null,scalar);
    }

    /**
     * @param name
     * @param x
     * @param y
     * @param z
     * @param scalar
     * @return
     */
    @Override
    public ScalarOp createScalarTransform(String name,
                                          INDArray x,
                                          INDArray y,
                                          INDArray z,
                                          double scalar) {
        return createScalarTransform(name,x,y,z,null,scalar);
    }

    /**
     * @param name
     * @param x
     * @param y
     * @param z
     * @param extraArgs
     * @param scalar
     * @return
     */
    @Override
    public ScalarOp createScalarTransform(String name,
                                          INDArray x,
                                          INDArray y,
                                          INDArray z,
                                          Object[] extraArgs,
                                          double scalar) {
        ScalarOp ret = null;
        switch(name) {
            case "add_scalar":
                ret = new ScalarAdd(x,y,z,x.length(),scalar);
                break;
            case "sub_scalar":
                ret = new ScalarSubtraction(x,y,z,x.length(),scalar);
                break;
            case "mul_scalar":
                ret = new ScalarMultiplication(x,y,z,x.length(),scalar);
                break;
            case "div_scalar":
                ret = new ScalarDivision(x,y,z,x.length(),scalar);
                break;
            case "equals_scalar":
                ret = new ScalarEquals(x,y,z,x.length(),scalar);
                break;
            case "notequals_scalar":
                ret = new ScalarNotEquals(x,y,z,x.length(),scalar);
                break;
            case "fmod_scalar":
                ret = new ScalarFMod(x,y,z,x.length(),scalar);
                break;
            case "max_scalar":
                ret = new ScalarMax(x,y,z,x.length(),scalar);
                break;
            case "min_scalar":
                ret = new ScalarMin(x,y,z,x.length(),scalar);
                break;
            case "greaterthan_scalar":
                ret = new ScalarGreaterThan(x,y,z,x.length(),scalar);
                break;
            case "greaterthanorequal_scalar":
                ret = new ScalarGreaterThanOrEqual(x,y,z,x.length(),scalar);
                break;
            case "lessthan_scalar":
                ret = new ScalarLessThan(x,y,z,x.length(),scalar);
                break;
            case "lessthanorequal_scalar":
                ret = new ScalarLessThanOrEqual(x,y,z,x.length(),scalar);
                break;
            case "remainder_scalar":
                ret = new ScalarRemainder(x,y,z,x.length(),scalar);
                break;
            case   "rdiv_scalar":
                ret = new ScalarReverseDivision(x,y,z,x.length(),scalar);
                break;
            case   "rsub_scalar":
                ret = new ScalarReverseSubtraction(x,y,z,x.length(),scalar);
                break;
        }

        ret.setExtraArgs(extraArgs);
        return ret;
    }

    @Override
    public BroadcastOp createBroadcastOp(String name, INDArray x, INDArray y, INDArray z, int... dimension) {
        return createBroadcastOp(name,x,y,z,null,dimension);
    }

    @Override
    public BroadcastOp createBroadcastOp(String name, INDArray x, INDArray y, INDArray z, Object[] extraArgs, int... dimension) {
        BroadcastOp broadcastOp = null;
        switch (name) {
            case "broadcastadd":
                broadcastOp = new BroadcastAddOp(x, y, z, dimension);
                break;
            case "broadcastsub":
                broadcastOp = new BroadcastSubOp(x, y, z, dimension);
                break;
            case "broadcastmul":
                broadcastOp = new BroadcastMulOp(x, y, z, dimension);
                break;
            case "broadcastdiv":
                broadcastOp = new BroadcastDivOp(x, y, z, dimension);
                break;
            case "broadcastrsub":
                broadcastOp = new BroadcastRSubOp(x, y, z, dimension);
                break;
            case "broadcastrdiv":
                broadcastOp = new BroadcastRDivOp(x, y, z, dimension);
                break;
            case "broadcastcopy":
                broadcastOp = new BroadcastCopyOp(x, y, z, dimension);
                break;
        }

        broadcastOp.setExtraArgs(extraArgs);
        return broadcastOp;
    }

    @Override
    public BroadcastOp createBroadcastOp(String name, INDArray x, INDArray y, int... dimension) {
        return createBroadcastOp(name,x,y,x,null,dimension);
    }
}
