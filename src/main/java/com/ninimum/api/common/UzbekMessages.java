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
        put("Parolni kiriting (72 belgidan oshmasin).", "Password is required (maximum 72 UTF-8 bytes).");
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
        // Courier app
        put("Tanlangan buyurtmalardan biri boshqa kuryerga berilgan. Hech biri olinmadi — ro‘yxat yangilandi, qaytadan tanlang.", "One of the selected deliveries was already taken by another courier");
        put("Bu buyurtmani boshqa kuryer olgan. Ro‘yxatni yangilang.", "This delivery was already taken by another courier");
        put("Kamida bitta buyurtmani tanlang.", "At least one delivery must be selected");
        put("Yo‘lga chiqishdan oldin tanlangan barcha buyurtmalarni qabul qiling.", "All selected deliveries must be accepted before starting the route");
        put("Tanlangan buyurtmalardan birini boshlab bo‘lmadi. Ro‘yxatni yangilang.", "Could not start one of the selected deliveries");
        put("Yetkazish holatini yangilab bo‘lmadi. Qayta urinib ko‘ring.", "Could not update delivery status");
        put("Buyurtma holati o‘zgargan. Ro‘yxatni yangilang.", "Order status changed; refresh the delivery");
        put("Bu buyurtma sizga biriktirilmagan.", "Delivery job does not belong to this courier");
        put("Yetkazish topilmadi.", "Delivery job not found");
        put("Kuryer topilmadi yoki faol emas.", "Delivery worker not found or inactive");
        put("Bunday ID li kuryer allaqachon bor.", "Delivery worker with this ID already exists");
        put("Kuryerni yaratib bo‘lmadi.", "Could not create delivery worker");
        put("Kuryer ID va parolni kiriting.", "Courier ID and password are required");
        put("Kuryer ID 3–50 belgi bo‘lishi va faqat harf, raqam, nuqta, chiziqcha yoki pastki chiziqdan iborat bo‘lishi kerak.", "Courier ID must be 3-50 characters and contain only letters, numbers, dot, dash or underscore");
        put("Kuryer ID, ism va parolni (kamida 6 belgi) kiriting.", "Courier ID, full name and password (minimum 6 characters) are required");
        put("Qaytarish sababini yozing (1–500 belgi).", "A return reason of 1-500 characters is required");
        put("Yetkazish holatini bu tartibda o‘zgartirib bo‘lmaydi.", "Invalid delivery status transition: ");
        // Customer app: orders, payment, tariffs, account
        put("Buyurtmada kamida bitta mahsulot bo‘lishi kerak.", "Order must contain at least one product");
        put("Buyurtmani yaratib bo‘lmadi. Qayta urinib ko‘ring.", "Order could not be created", "Calculated order total is invalid");
        put("Mahsulot topilmadi yoki narxi noto‘g‘ri.", "Product not found or price is invalid. product_id=");
        put("Miqdor 0 dan katta bo‘lishi kerak.", "Quantity must be greater than zero. product_id=");
        put("Buyurtma mahsulotlari topilmadi.", "Order items not found for order_id=");
        put("Bu buyurtma allaqachon to‘langan.", "Order already paid");
        put("Buyurtma bekor qilingan.", "Order is cancelled");
        put("Buyurtmani bekor qilib bo‘lmaydi: u allaqachon tayyorlanmoqda, yetkazilgan yoki bekor qilingan.", "Order cannot be cancelled. It may already be processing, delivered, cancelled, or not belong to this user");
        put("Bekor qilish sababini yozing.", "Cancel reason is required");
        put("Shahrisabzdagi yetkazish manzilini tanlang.", "Please select a delivery address in Shahrisabz");
        put("Yetkazish manzilini saqlab bo‘lmadi.", "Delivery address could not be saved");
        put("Hududni o‘zgartirib bo‘lmadi.", "Region cannot be changed. User or active region was not found");
        put("Tarif topilmadi yoki faol emas.", "Tariff not found or inactive", "Tariff subscription not found");
        put("Tarif narxi noto‘g‘ri.", "Tariff price is invalid");
        put("Bu tarif allaqachon faol.", "Selected tariff is already active");
        put("Tarif uchun to‘lovni boshlab bo‘lmadi. Qayta urinib ko‘ring.", "Tariff checkout could not be created", "Tariff subscription activation failed. subscriptionId=");
        put("Tarif faqat Payme orqali yoqiladi.", "Direct tariff activation is disabled. Use Payme checkout.");
        put("Faol tarifni bekor qilib bo‘lmadi. U allaqachon tugagan yoki bekor qilingan bo‘lishi mumkin.", "Active tariff could not be cancelled. It may already be expired/cancelled or not belong to this user");
        put("Sizda faol tarif yo‘q.", "Tariff subscription is not active for this user");
        put("Karta raqami va amal qilish muddatini kiriting.", "Card number and expire are required");
        put("To‘lov tizimida xatolik. Qayta urinib ko‘ring.", "Payme error: ", "Payme Subscribe API HTTP error: ");
        put("Login yoki parol noto‘g‘ri.", "Invalid login or password", "Password is incorrect");
        put("Yangi parol eskisidan farq qilishi kerak.", "New password must be different from current password");
        put("Bu telefon raqami allaqachon ro‘yxatdan o‘tgan.", "Phone number already exists");
        put("Telefon raqami va vaqtinchalik parolni kiriting.", "Phone number and temporary password are required");
        put("SMS yuborib bo‘lmadi. Birozdan keyin qayta urinib ko‘ring.", "Eskiz login failed", "HTTP Request failed with status: ", "Refresh token failed with status code: ");
        put("Administrator topilmadi.", "Admin not found");
        put("MXIK paketini tanlang.", "Fiscal MXIK package must be selected");
        put("Mahsulot ma’lumotlarini kiriting.", "Product data is required", "Product ID is required");
        put("Majburiy ma’lumotlar yetishmayapti. Ilovani yangilab, qayta urinib ko‘ring.", "Order ID and User ID are required", "User ID and Region ID are required", "User ID and phone number are required", "User ID is required",
            "User ID, current password and new password are required", "jobId and status are required", "jobId is required", "order_id is required", "subscriptionId is required", "tariffId is required", "userId is required",
            "Token and code are required", "Token is required");
        put("Serverda xatolik yuz berdi. Birozdan keyin qayta urinib ko‘ring.", "Failed to map CamelCaseMap to ", "Failed to parse timestamp: ");
        put("Kirish ma’lumotlari noto‘g‘ri. Qaytadan kiring.", "The token does not contain authorization information.");
        // Taxi delivery
        put("Taksi orqali yetkazish faqat ish vaqtida mavjud. Kuryer orqali yetkazishni tanlang.", "Taxi delivery is available only during working hours");
        put("Avval taksi chaqirilgan bo‘lishi kerak.", "A booked taxi is required");
        put("Bu buyurtma hozir yo‘lda emas.", "This order is not on the way");
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
        // Messages built as "text: value" are matched by their fixed start.
        for (var e : UZ.entrySet())
            if ((e.getKey().endsWith(": ") || e.getKey().endsWith("=")) && message.startsWith(e.getKey())) return e.getValue();
        return message;
    }
}
