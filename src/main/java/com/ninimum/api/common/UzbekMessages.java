package com.ninimum.api.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Every error sentence the backend sends is shown to people as is, so it is turned into Uzbek here, in one place,
 * just before the response leaves the server (ResponseResult.getResultMsg). Error codes such as WAREHOUSE_INVALID_INPUT
 * are not touched: the apps translate those themselves and some app logic depends on them.
 */
public final class UzbekMessages {
    private UzbekMessages() {}
    private static final Map<String,String> UZ = new HashMap<>();
    private static void put(String uz, String... english) { for (String e : english) UZ.put(e, uz); }
    static {
        // Result enum and general
        put("Amal bajarilmadi.", "Failed.");
        put("Foydalanuvchi allaqachon mavjud.", "User exist");
        put("Foydalanuvchi topilmadi.", "User is not exist", "Authenticated user was not found.", "Unknown User value: ");
        put("Ma’lumot topilmadi.", "Company is not exist", "Branch is not exist", "Poster is not exist", "Poster feedback is not exist", "Promotion is not exist", "Not found!");
        put("Bunday ma’lumot allaqachon mavjud.", "Company is exist", "Branch is exist", "Poster is exist", "Poster comment is exist", "Poster feedback is exist", "Promotion is exist");
        put("Parol noto‘g‘ri.", "Password is not matched!", "Password is not matched");
        put("Topildi.", "Found!");
        put("Foydalanuvchi bloklangan.", "User blocked!", "User is banned. Access denied.");
        put("Foydalanuvchi o‘chirilgan.", "User deleted!");
        put("Kirish ma’lumotlari noto‘g‘ri. Qaytadan kiring.", "Invalid token information.", "Unsupported token information.", "Token information cannot be verified.", "Empty token");
        put("Sessiya muddati tugadi. Qaytadan kiring.", "This token is expired.");
        put("Boshqa qurilmada kirilgan. Qaytadan kiring.", "Duplicate login.");
        put("Avval tizimga kiring.", "Please log in first.", "User is not authenticated.", "Customer authentication required", "Authenticated customer not found");
        put("Telefon raqamini tasdiqlab bo‘lmadi.", "The phone verification failed.");
        put("Vaqtinchalik parolni yuborib bo‘lmadi.", "Temp password failed.");
        put("Kirish ma’lumotlarini tekshirib bo‘lmadi. Qaytadan kiring.", "Your authentication information cannot be verified.");
        put("Serverda xatolik yuz berdi. Birozdan keyin qayta urinib ko‘ring.", "Something went wrong on our end. We're working to fix it.", "JPEG writer is not available.", "Invalid stored inventory document");
        put("Tizim xatosi yuz berdi. Administratorga murojaat qiling.", "A system error has occurred. Please contact your administrator.");
        put("Rol noto‘g‘ri.", "Role is invalid");
        put("Bu amal uchun ruxsatingiz yo‘q.", "Access is denied", "Forbidden", "Owner only", "Owner approval required", "Only the owner can change timers", "Only the owner can correct an order status", "Only the owner can merge customers");
        put("Hisob mavjud emas.", "Account unavailable");
        put("Juda ko‘p urinish. 15 daqiqadan keyin qayta urinib ko‘ring.", "Too many attempts. Try again in 15 minutes.");
        put("Kirish turi noto‘g‘ri.", "Invalid login realm");
        put("Telefon raqami noto‘g‘ri.", "Invalid phone number");
        put("Parol kamida 10 belgidan iborat bo‘lishi va katta harf, kichik harf, raqam hamda belgi bo‘lishi kerak.", "Password must contain at least 10 characters, uppercase, lowercase, a number and a symbol (maximum 72 UTF-8 bytes).");
        put("Administrator topilmadi yoki egasining hisobini o‘zgartirib bo‘lmaydi.", "Administrator not found or owner account is protected");
        put("Administrator holati noto‘g‘ri.", "Invalid administrator status");
        // Orders
        put("Buyurtma topilmadi.", "Order not found", "Order does not belong to the authenticated customer");
        put("Buyurtma holatini tanlang.", "Order status is required");
        put("Bekor qilish sababini yozing.", "Cancellation reason is required");
        put("Sababni yozing (1–500 belgi).", "A reason of 1–500 characters is required");
        put("Bu buyurtmani bekor qilib bo‘lmaydi.", "This order cannot be cancelled");
        put("Buyurtma o‘zgargan. Sahifani yangilab, qayta urinib ko‘ring.", "The order changed. Refresh it and try again", "Delivery changed; refresh the order");
        put("Buyurtmani ombor va kuryer ilovalari orqali yuriting. Bu yerda faqat bekor qilish mumkin.", "Use the warehouse and courier apps to advance the order. Only cancellation is allowed here.");
        put("Faqat to‘langan buyurtmani oldingi bosqichga qaytarish mumkin.", "Only a paid order can be moved to an earlier operational stage");
        put("Qisman qaytarilgan buyurtmani tayyorlash yoki yetkazish bosqichiga qaytarib bo‘lmaydi.", "An order with a partial return cannot be moved back to preparation or delivery");
        put("Bekor qilingan yetkazishni bu yerda qayta ochib bo‘lmaydi.", "A cancelled delivery cannot be reopened here");
        put("Faol, biriktirilgan kuryer kerak.", "An active assigned courier is required");
        put("Avval ombor buyurtmani tekshirib, qadoqlashi kerak.", "The warehouse must have checked and packed the order");
        put("\"Tasdiqlandi\" holatidan qayta boshlang, shunda faol omborchi buyurtmani oladi.", "Restart at Confirmed so an active warehouse worker can claim the order");
        put("Buyurtmada to‘liq yetkazish manzili yo‘q.", "Order has no complete delivery address");
        put("Shahrisabzdagi to‘g‘ri manzilli buyurtmani tanlang.", "Choose an order with a valid Shahrisabz address");
        put("O‘zingizning to‘langan va yetkazilgan buyurtmangizni tanlang.", "Choose your own paid delivered order");
        put("To‘lovdan oldin buyurtma narxini yangilang.", "Refresh the order price before checkout");
        put("Buyurtma tarkibi noto‘g‘ri.", "Invalid order items");
        put("To‘lov holati noto‘g‘ri.", "Invalid payment status");
        put("Sana oralig‘i noto‘g‘ri.", "Invalid date range");
        put("Sana noto‘g‘ri.", "Invalid timestamp: ");
        put("Miqdor musbat son bo‘lishi kerak.", "Positive quantity required");
        put("Raqam noto‘g‘ri.", "Invalid number");
        put("Ha yoki yo‘q qiymati kerak.", "Boolean required");
        put("Mijozlar bo‘limi noto‘g‘ri.", "Invalid customer section");
        // Recurring orders and tariffs
        put("Takroriy buyurtma topilmadi.", "Recurring order not found");
        put("Takroriy buyurtma to‘xtatilmagan.", "Recurring order is not paused");
        put("Faol tarifingizda takroriy buyurtma yo‘q.", "Active tariff does not include recurring orders");
        put("Oldingi takroriy buyurtma hali to‘lovni kutmoqda.", "Previous recurring draft still awaits payment");
        put("Takroriy buyurtmani yaratib bo‘lmadi. Qoldiq, manzil va tarifni tekshiring.", "Cannot create recurring draft; review stock, address and tariff");
        put("Roziligingiz va eng yuqori summa kerak.", "Explicit consent and maximum total are required");
        put("Joriy summa siz belgilagan eng yuqori summadan oshib ketdi.", "Current total exceeds your maximum");
        put("Narx siz belgilagan eng yuqori summadan oshdi. Tekshirib chiqing.", "Price exceeds your maximum; review required");
        put("Tarif muddati o‘zgardi. Qayta ko‘rib chiqing va rozilik bering.", "Tariff interval changed; review and consent again");
        put("Takrorlanish oralig‘i noto‘g‘ri.", "Invalid recurring interval");
        put("Imtiyoz qiymati noto‘g‘ri.", "Invalid benefit value");
        // Courier dispatch
        put("Bu taklif sizga biriktirilmagan.", "This delivery offer is not assigned to you");
        put("Taklif muddati tugagan yoki boshqa kuryerga berilgan.", "This offer expired or belongs to another courier");
        put("Bu ish kuryer tayinlashni kutmayapti.", "Job is not awaiting dispatch");
        put("Faqat yuqoriga uzatilgan takliflarni ko‘rib chiqish mumkin.", "Only escalated offers can be reviewed");
        put("Ko‘rib chiqish sababini yozing.", "A review reason is required");
        put("Qabul qilish vaqti 1–60 daqiqa bo‘lishi kerak.", "Acceptance timeout must be 1–60 minutes", "Invalid acceptance timeout");
        // Timers
        put("Taymer 1–43200 daqiqa bo‘lishi kerak.", "Timer must be 1–43200 minutes");
        put("Shikoyat taymeri noto‘g‘ri.", "Invalid complaint timer");
        put("Javob muddati noto‘g‘ri.", "Invalid response deadline");
        put("Ish jadvali noto‘g‘ri.", "Invalid working schedule");
        put("Ish vaqti noto‘g‘ri.", "Invalid working time");
        put("Muddat sozlamasi noto‘g‘ri.", "Invalid expiry setting");
        // Products and categories
        put("Kategoriya topilmadi.", "Category does not exist", "Parent category does not exist");
        put("Kategoriya nomini kiriting.", "Category name is required");
        put("Kategoriya nomi juda uzun.", "Category name too long");
        put("Kategoriyalar ierarxiyasi aylana bo‘lib qolmasligi kerak.", "Category hierarchy cannot contain a cycle");
        put("Asosiy kategoriya noto‘g‘ri.", "Invalid parent category");
        put("Kategoriya rasmi manzili noto‘g‘ri.", "Invalid category image URL");
        put("Rasm manzili juda uzun.", "Image URL too long");
        put("Mahsulot narxi noto‘g‘ri.", "Invalid product price");
        put("Tannarx noto‘g‘ri.", "Invalid cost price");
        put("Narx o‘zgartirish sababi juda uzun.", "Price reason is too long");
        put("Qo‘shimcha ma’lumot juda uzun.", "Metadata too long");
        put("Narx qoidasi noto‘g‘ri.", "Invalid price rule");
        put("Mahsulot qoidasi noto‘g‘ri.", "Invalid product rule");
        put("Bir mahsulot uchun qoida takrorlangan.", "Duplicate product rule");
        put("Mahsulot qoidalarini kiriting (500 tagacha).", "Product rules required (maximum 500)");
        put("Savol turi noto‘g‘ri.", "Invalid question type");
        // Images
        put("Rasm tanlanmagan.", "Image data is required.", "Product image is empty.", "Review image is empty.");
        put("Rasm noto‘g‘ri.", "Invalid image data.", "Unsupported image", "Unsupported review image format.");
        put("Faqat rasm yuklash mumkin.", "Only image files can be uploaded.", "Only image files can be uploaded for a review.");
        put("Rasm hajmi 2 MB dan oshmasligi kerak.", "Image must be at most 2 MB");
        put("Rasm o‘lchami juda katta.", "Image dimensions too large");
        put("Mahsulot rasmi juda katta. Eng ko‘pi 20 MB.", "Product image is too large. Maximum size is 20 MB.");
        put("Sharh rasmi juda katta.", "Review image is too large.");
        // Warehouse setup (shown to the owner)
        put("Ombor hali sozlanmagan (warehouse-step1).", "Run warehouse-step1.sql before enabling WAREHOUSE_ENABLED");
        put("Ombor ilovasi hali yoqilmagan (warehouse-step2).", "Run warehouse-step2.sql and enable warehouse.enabled first");
        put("Noto‘g‘ri qiymat.", "Invalid ");
    }

    /** The Uzbek text for a known English sentence, else the message unchanged. */
    public static String of(String message) {
        if (message == null) return null;
        String uz = UZ.get(message);
        if (uz != null) return uz;
        for (String prefix : new String[]{"Invalid timestamp: ", "Unknown User value: "})
            if (message.startsWith(prefix)) return UZ.get(prefix);
        return message;
    }
}
