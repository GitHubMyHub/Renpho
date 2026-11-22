package com.omnitracker.renpho.data.local

import com.omnitracker.renpho.domain.MeasurementDataSource
import com.omnitracker.renpho.domain.model.Measurement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMeasurementDataSource(private val dao: MeasurementDao) : MeasurementDataSource {

    override fun observeAll(): Flow<List<Measurement>> =
        dao.observeAll().map { list -> list.map { it.toMeasurement() } }

    override fun observe(id: Long): Flow<Measurement?> = dao.observe(id).map { it?.toMeasurement() }

    override suspend fun insert(measurement: Measurement): Long = dao.insert(measurement.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun deleteAll() = dao.deleteAll()
}
