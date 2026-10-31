package io.github.j0p3s.debloater.data

/**
 * Phone makers and app publishers. Used to group packages by vendor, to detect the brand
 * of this phone, and to guess the vendor of packages that are not in the debloat list.
 *
 * @param brands lower-case values of Build.MANUFACTURER / Build.BRAND that belong to this vendor.
 * @param prefixes package name prefixes that belong to this vendor.
 * @param universal true for publishers found on phones of any brand (Google, Facebook, carriers…).
 */
data class Vendor(
    val name: String,
    val brands: List<String> = emptyList(),
    val prefixes: List<String> = emptyList(),
    val universal: Boolean = false,
)

object VendorCatalog {
    const val OTHER = "Other"

    val all: List<Vendor> = listOf(
        Vendor("Samsung", listOf("samsung"), listOf("com.samsung.", "com.sec.", "com.osp.", "com.wssyncmldm", "com.skms.")),
        Vendor(
            "Xiaomi / Redmi / POCO", listOf("xiaomi", "redmi", "poco"),
            listOf("com.miui.", "com.xiaomi.", "com.mi.", "com.duokan.", "com.mipay.", "com.lbe.security.miui", "com.facemoji.lite.xiaomi"),
        ),
        Vendor("Nubia / RedMagic", listOf("nubia", "redmagic", "red magic"), listOf("cn.nubia.", "com.nubia.")),
        Vendor("ZTE", listOf("zte"), listOf("com.zte.")),
        Vendor("LG", listOf("lge", "lg", "lg electronics"), listOf("com.lge.")),
        Vendor(
            "OnePlus / OPPO / Realme", listOf("oneplus", "oppo", "realme"),
            listOf("com.oneplus.", "net.oneplus.", "com.oplus.", "com.coloros.", "com.heytap.", "com.oppo.", "com.nearme.", "com.realme."),
        ),
        Vendor("Motorola", listOf("motorola"), listOf("com.motorola.")),
        Vendor("Huawei / Honor", listOf("huawei", "honor"), listOf("com.huawei.", "com.hihonor.")),
        Vendor("Sony", listOf("sony"), listOf("com.sonymobile.", "com.sonyericsson.", "com.sony.")),
        Vendor("vivo / iQOO", listOf("vivo", "iqoo"), listOf("com.vivo.", "com.bbk.", "com.iqoo.")),
        Vendor("ASUS", listOf("asus"), listOf("com.asus.")),
        Vendor("Nokia / HMD", listOf("hmd global", "nokia", "hmd"), listOf("com.hmdglobal.", "com.evenwell.")),
        Vendor(
            "TECNO / Infinix / itel", listOf("tecno", "infinix", "itel", "transsion"),
            listOf("com.transsion.", "com.transsnet.", "com.afmobi.", "com.talpa.", "net.bat.store", "com.funbase."),
        ),
        Vendor("Lenovo", listOf("lenovo"), listOf("com.lenovo.")),
        Vendor("Nothing", listOf("nothing"), listOf("com.nothing.")),
        Vendor(
            "Qualcomm / MediaTek",
            prefixes = listOf("com.qualcomm.", "com.qti.", "vendor.qti.", "org.codeaurora.", "com.quicinc.", "com.mediatek.", "com.mtk."),
            universal = true,
        ),
        Vendor("Google", listOf("google"), listOf("com.google."), universal = true),
        Vendor("AOSP", prefixes = listOf("com.android."), universal = true),
        Vendor("Facebook", prefixes = listOf("com.facebook.", "com.instagram."), universal = true),
        Vendor("Microsoft", prefixes = listOf("com.microsoft.", "com.skype.", "com.linkedin."), universal = true),
        Vendor("Amazon", prefixes = listOf("com.amazon.", "in.amazon."), universal = true),
        Vendor("Carrier", prefixes = listOf("com.att.", "com.vzw.", "com.verizon.", "com.tmobile.", "com.vodafone.", "com.orange."), universal = true),
        Vendor("Preinstalled", universal = true),
    )

    private val byName = all.associateBy { it.name }

    fun byName(name: String): Vendor? = byName[name]

    /** Brand of this phone from Build.MANUFACTURER / Build.BRAND, e.g. ("Xiaomi", "Redmi") -> Xiaomi / Redmi / POCO. */
    fun detect(manufacturer: String?, brand: String?): Vendor? {
        val values = listOfNotNull(brand, manufacturer).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        // Brand first: a "nubia" manufacturer can sell under the "RedMagic" brand and so on.
        for (v in values) {
            all.firstOrNull { vendor -> !vendor.universal && vendor.brands.any { matches(v, it) } }?.let { return it }
        }
        for (v in values) {
            all.firstOrNull { vendor -> vendor.universal && vendor.brands.any { matches(v, it) } }?.let { return it }
        }
        return null
    }

    /** "lge" matches "lge", "tecno" matches "tecno mobile limited", but "lg" does not match "lgx". */
    private fun matches(value: String, key: String) = value == key || value.startsWith("$key ")

    /** Vendor of a package: from the debloat list if listed, otherwise guessed from its name. */
    fun vendorOf(packageName: String, listedVendor: String?): String =
        listedVendor ?: all.firstOrNull { v -> v.prefixes.any { packageName.startsWith(it) } }?.name ?: OTHER
}
