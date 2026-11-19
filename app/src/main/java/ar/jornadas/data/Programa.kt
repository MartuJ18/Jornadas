package ar.jornadas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

data class FichaRemota(val id: String, val title: String, val track: String, val day: Int,
                       val time: String, val room: String, val speaker: String, val summary: String)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
                  val hora: String, val sala: String, val ponente: String, val resumen: String,
                  val favorita: Boolean = false)

// Datos que manda el servidor: todo menos la marca del usuario.
data class DatosCharla(val id: String, val titulo: String, val tema: String, val dia: Int,
                       val hora: String, val sala: String, val ponente: String, val resumen: String)

// Una fila = hubo al menos una descarga exitosa (aunque viniera vacía).
@Entity(tableName = "edicion")
data class Edicion(@PrimaryKey val clave: Int = 0, val recibida: Long)

@Dao
abstract class CarteleraDao {
    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    abstract fun observar(): Flow<List<Charla>>

    @Query("SELECT EXISTS(SELECT 1 FROM edicion)")
    abstract fun observarEdicion(): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM edicion)")
    abstract suspend fun hayEdicion(): Boolean

    @Query("UPDATE charlas SET favorita = NOT favorita WHERE id = :id")
    abstract suspend fun alternar(id: String)

    @Query("DELETE FROM charlas WHERE id NOT IN (:ids)")
    protected abstract suspend fun borrarRetiradas(ids: List<String>)

    @Query("DELETE FROM charlas")
    protected abstract suspend fun borrarTodas()

    @Update(entity = Charla::class)
    protected abstract suspend fun actualizarDatos(fichas: List<DatosCharla>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertarNuevas(fichas: List<Charla>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun registrarEdicion(edicion: Edicion)

    @Transaction
    open suspend fun aplicarEdicion(fichas: List<DatosCharla>) {
        if (fichas.isEmpty()) borrarTodas() else borrarRetiradas(fichas.map { it.id })
        actualizarDatos(fichas)
        insertarNuevas(fichas.map {
            Charla(it.id, it.titulo, it.tema, it.dia, it.hora, it.sala, it.ponente, it.resumen)
        })
        registrarEdicion(Edicion(recibida = System.currentTimeMillis()))
    }
}

@Database(entities = [Charla::class, Edicion::class], version = 2, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}

// Pasa de la base v1 (ya instalada en los usuarios) a la v2 sin perder datos.
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `edicion` (`clave` INTEGER NOT NULL, `recibida` INTEGER NOT NULL, PRIMARY KEY(`clave`))")
        db.execSQL("INSERT INTO edicion (clave, recibida) SELECT 0, 0 WHERE EXISTS (SELECT 1 FROM charlas)")
    }
}