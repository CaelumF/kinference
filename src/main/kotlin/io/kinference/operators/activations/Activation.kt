package io.kinference.operators.activations

import io.kinference.ndarray.NDArray
import io.kinference.ndarray.PrimitiveToPrimitiveFunction
import io.kinference.primitives.types.DataType
import io.kinference.attributes.Attribute
import io.kinference.data.tensors.Tensor
import io.kinference.data.tensors.asTensor
import io.kinference.graph.Context
import io.kinference.ndarray.*
import io.kinference.operators.Operator
import io.kinference.operators.OperatorInfo

abstract class Activation(info: OperatorInfo, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>)
    : Operator<Tensor, Tensor>(info, attributes, inputs, outputs) {

    open fun activate(input: Tensor): Tensor = this.activate(input.data).asTensor()
    abstract fun activate(input: NDArray): NDArray

    override fun apply(context: Context, inputs: List<Tensor?>): List<Tensor?> {
        return listOf(activate(inputs.first()!!))
    }

    @ExperimentalUnsignedTypes
    companion object {
        // TODO: Add activations with alpha and beta
        fun createFloat(name: String): FloatMap = when (name) {
            "Sigmoid" -> Sigmoid.activateFloat
            "Tanh" -> Tanh.activateFloat
            "Relu" -> Relu.activateFloat
            else -> throw UnsupportedOperationException()
        }

        fun createDouble(name: String): DoubleMap = when (name) {
            "Sigmoid" -> Sigmoid.activateDouble
            "Tanh" -> Tanh.activateDouble
            "Relu" -> Relu.activateDouble
            else -> throw UnsupportedOperationException()
        }

        fun create(name: String, type: DataType): PrimitiveToPrimitiveFunction = when (type) {
            DataType.DOUBLE -> createDouble(name)
            DataType.FLOAT -> createFloat(name)
            else -> throw UnsupportedOperationException()
        }
    }
}
