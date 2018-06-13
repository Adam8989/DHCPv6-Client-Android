package be.mygod.dhcpv6client.room

import android.arch.persistence.room.*

@Entity
data class InterfaceStatement(@PrimaryKey val iface: String, var statements: String) {
    @android.arch.persistence.room.Dao
    interface Dao {
        @Query("SELECT * FROM `InterfaceStatement` ORDER BY `iface`")
        fun list(): List<InterfaceStatement>

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        fun createDefault(value: InterfaceStatement): Long

        @Update
        fun update(value: InterfaceStatement): Int

        @Query("DELETE FROM `InterfaceStatement` WHERE `iface` = :iface")
        fun delete(iface: String)
    }

    override fun toString() = "interface $iface $statements\n"
}
