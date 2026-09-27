package cn.zbx1425.mtrsteamloco.data

import mtr.data.Train

@JvmSuppressWildcards
interface TrainExtraSupplier {
    fun getCustomConfigs(): MutableMap<String?, String?>?
    fun setCustomConfigs(customConfigs: MutableMap<String?, String?>?)
    fun isConfigsChanged(): Boolean
    fun isConfigsChanged(isConfigsChanged: Boolean)
    fun getConfigResponders(): MutableMap<String?, ConfigResponder?>?
    fun setConfigResponders(configResponders: MutableMap<String?, ConfigResponder?>?)
    fun getRollAngleAt(rv: Double): Float

    companion object {
        @JvmStatic fun getRollAngleAt(train: Train?, car: Int): Float {
            val reversed = train!!.isReversed
            val firstCar = if (reversed) train.trainCars - car else car
            val nextCar = car + 1
            val secondCar = if (reversed) train.trainCars - nextCar else nextCar
            val progress = train.railProgress
            // Preserve legacy integer overflow before promoting the product to double.
            val first = progress - firstCar * train.spacing
            val second = progress - secondCar * train.spacing
            val supplier = train as TrainExtraSupplier
            return (supplier.getRollAngleAt(first) + supplier.getRollAngleAt(second)) / 2
        }
    }
}
