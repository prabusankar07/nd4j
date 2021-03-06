package org.nd4j.autodiff.functions.mmul;

import com.google.common.primitives.Ints;
import lombok.NoArgsConstructor;
import org.nd4j.autodiff.ArrayField;
import org.nd4j.autodiff.Field;
import org.nd4j.autodiff.functions.AbstractBinaryReduceFunction;
import org.nd4j.autodiff.functions.DifferentialFunction;
import org.nd4j.autodiff.functions.DifferentialFunctionFactory;
import org.nd4j.autodiff.graph.Graph;
import org.nd4j.autodiff.opstate.NDArrayInformation;
import org.nd4j.autodiff.opstate.OpState;
import org.nd4j.autodiff.samediff.SDGraph;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.linalg.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Tensor matrix multiply operation
 *
 * @author Adam Gibson
 */
@NoArgsConstructor
public class TensorMmul<X extends Field<ArrayField>> extends AbstractBinaryReduceFunction<X> {
    private int argNum;
    private int[][] axes;
    protected boolean addedEdges;

    public TensorMmul(SameDiff sameDiff,
                      DifferentialFunction<ArrayField> i_v1,
                      DifferentialFunction<ArrayField> i_v2,
                      int[][] dimensions,
                      int argNum) {
        super(sameDiff);
        this.sameDiff = sameDiff;
        this.axes = dimensions;
        this.argNum = argNum;
        this.extraArgs = new Object[] {axes};
        this.m_x1 = i_v1;
        this.m_x2 = i_v2;
        if(!addedEdges) {
            ArrayField a = i_v1.getValue(true);
            ArrayField b = i_v2.getValue(true);

            addEdges(sameDiff,
                    i_v1,
                    i_v2,
                    functionName(),
                    OpState.OpType.ACCUMULATION,
                    ArrayUtil.getTensorMmulShape(a.getInput().getShape(), b.getInput().getShape(), dimensions));
            addedEdges = true;
        }
    }


    @Override
    protected void addEdges(SameDiff sameDiff,
                            DifferentialFunction<ArrayField> i_v1,
                            DifferentialFunction<ArrayField> i_v2,
                            String opName) {
        if(i_v1.getValue(true) instanceof ArrayField && axes != null
                && !addedEdges) {
            addedEdges = true;
            ArrayField arrayField = (ArrayField) i_v1.getValue(true);
            ArrayField secondVal = (ArrayField) i_v2.getValue(true);

            addEdges(sameDiff,i_v1,i_v2,opName,
                    OpState.OpType.ACCUMULATION,
                    ArrayUtil.getTensorMmulShape(arrayField.getInput()
                                    .getShape(),
                            secondVal.getInput().getShape(),
                            axes),new Object[]{argNum});

        }

    }

    /**
     * Get the value of this function
     *
     * @return
     */
    @Override
    public ArrayField doGetValue() {
        return sameDiff.getArrayFactory().tensorMmul(larg(),rarg(),axes);
    }



    @Override
    public String functionName() {
        return "tensorMmul";
    }



    @Override
    public DifferentialFunction<ArrayField> diff(DifferentialFunction<ArrayField> i_v1) {
        return doTensorMmul(argNum,larg(),rarg());
    }




    private DifferentialFunction<ArrayField> doTensorMmul(int argNum,
                                                          DifferentialFunction<ArrayField> a,
                                                          DifferentialFunction<ArrayField> b) {
        if (a.getValue(true) instanceof ArrayField) {
            ArrayField xField = (ArrayField) a.getValue(true);
            ArrayField yField = (ArrayField) b.getValue(true);
            int validationLength = Math.min(axes[0].length, axes[1].length);
            for (int i = 0; i < validationLength; i++) {
                if (xField.getInput().getShape()[axes[0][i]] != yField.getInput().getShape()[axes[1][i]])
                    throw new IllegalArgumentException("Size of the given axes at each dimension must be the same size.");
                if (axes[0][i] < 0)
                    axes[0][i] += xField.getInput().getShape().length;
                if (axes[1][i] < 0)
                    axes[1][i] += yField.getInput().getShape().length;

            }

            List<Integer> listA = new ArrayList<>();
            for (int i = 0; i < xField.getInput().getShape().length; i++) {
                if (!Ints.contains(axes[0], i))
                    listA.add(i);
            }

            int[] newAxesA = Ints.concat(Ints.toArray(listA), axes[0]);


            List<Integer> listB = new ArrayList<>();
            for (int i = 0; i < yField.getInput().getShape().length; i++) {
                if (!Ints.contains(axes[1], i))
                    listB.add(i);
            }

            int[] newAxesB = Ints.concat(axes[1], Ints.toArray(listB));

            int n2 = 1;
            int aLength = Math.min(xField.getInput().getShape().length, axes[0].length);
            for (int i = 0; i < aLength; i++) {
                n2 *= xField.getInput().getShape()[axes[0][i]];
            }

            //if listA and listB are empty these do not initialize.
            //so initializing with {1} which will then get overridden if not empty
            int[] newShapeA = {-1, n2};
            int[] oldShapeA;
            if (listA.size() == 0) {
                oldShapeA = new int[] {1};
            } else {
                oldShapeA = Ints.toArray(listA);
                for (int i = 0; i < oldShapeA.length; i++)
                    oldShapeA[i] = xField.getInput().getShape()[oldShapeA[i]];
            }

            int n3 = 1;
            int bNax = Math.min(yField.getInput().getShape().length, axes[1].length);
            for (int i = 0; i < bNax; i++) {
                n3 *= yField.getInput().getShape()[axes[1][i]];
            }


            int[] newShapeB = {n3, -1};
            int[] oldShapeB;
            if (listB.size() == 0) {
                oldShapeB = new int[] {1};
            } else {
                oldShapeB = Ints.toArray(listB);
                for (int i = 0; i < oldShapeB.length; i++)
                    oldShapeB[i] = yField.getInput().getShape()[oldShapeB[i]];
            }


            DifferentialFunction<ArrayField> at = getSameDiff()
                    .getFunctionFactory()
                    .reshape(getSameDiff().getFunctionFactory().permute
                            (a,newAxesA),newShapeA);
            DifferentialFunction<ArrayField> bt = getSameDiff().getFunctionFactory()
                    .reshape(getSameDiff().getFunctionFactory()
                            .permute(b,newAxesB),newShapeB);

            DifferentialFunction<ArrayField> ret = getSameDiff().getFunctionFactory().mmul(argNum,at,bt);
            int[] aPlusB = Ints.concat(oldShapeA, oldShapeB);
            return getSameDiff().getFunctionFactory().reshape(ret,aPlusB);

        }

        throw new IllegalStateException("Op type must be ArrayField");

    }
}
