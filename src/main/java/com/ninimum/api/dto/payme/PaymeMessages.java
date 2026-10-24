package com.ninimum.api.dto.payme;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Payme shows the error "message" of our merchant replies to the payer. Sent as {"ru","uz","en"} (Payme's format),
 * the customer reads a clear sentence in their language instead of a generic "server error".
 */
public final class PaymeMessages {
    private PaymeMessages() {}
    private static final Map<String,String[]> TEXT = new HashMap<>();
    private static void put(String english, String uz, String ru) { TEXT.put(english, new String[]{uz, ru, english}); }
    static {
        String sys = "To‘lovni tekshirishda do‘kon serverida xatolik yuz berdi. Birozdan keyin qayta urinib ko‘ring.";
        String sysRu = "Ошибка на сервере магазина при проверке платежа. Повторите попытку позже.";
        put("System error", sys, sysRu);
        put("Tariff amount is invalid", sys, sysRu);
        put("Order amount is invalid", sys, sysRu);
        put("Invalid Request", sys, sysRu);
        put("Method not found", sys, sysRu);
        put("Invalid params", sys, sysRu);
        put("Password is invalid", "Do‘kon to‘lov tizimiga ulanishda xatolik. Keyinroq urinib ko‘ring.", "Ошибка подключения магазина к платёжной системе. Повторите позже.");
        put("Payment account not found", "Buyurtma topilmadi. Ilovaga qaytib, buyurtmani qayta rasmiylashtiring.", "Заказ не найден. Вернитесь в приложение и оформите заказ заново.");
        put("Order not found", "Buyurtma topilmadi. Ilovaga qaytib, buyurtmani qayta rasmiylashtiring.", "Заказ не найден. Вернитесь в приложение и оформите заказ заново.");
        put("Subscription not found", "Tarif topilmadi. Ilovaga qaytib, qayta urinib ko‘ring.", "Тариф не найден. Вернитесь в приложение и повторите.");
        put("Only one payment account is allowed", "To‘lov ma’lumotlari noto‘g‘ri. Ilovaga qaytib, qayta urinib ko‘ring.", "Неверные данные платежа. Вернитесь в приложение и повторите.");
        put("Transaction id is invalid", "To‘lov ma’lumotlari noto‘g‘ri. Ilovaga qaytib, qayta urinib ko‘ring.", "Неверные данные платежа. Вернитесь в приложение и повторите.");
        put("Amount is invalid", "To‘lov summasi noto‘g‘ri. Ilovaga qaytib, buyurtmani yangilang.", "Неверная сумма. Вернитесь в приложение и обновите заказ.");
        put("Incorrect amount", "To‘lov summasi buyurtmaga mos emas. Ilovaga qaytib, buyurtmani yangilang.", "Сумма не совпадает с заказом. Вернитесь в приложение и обновите заказ.");
        put("Order already paid", "Bu buyurtma allaqachon to‘langan.", "Этот заказ уже оплачен.");
        put("Subscription already paid", "Bu tarif allaqachon to‘langan.", "Этот тариф уже оплачен.");
        put("Order is cancelled", "Buyurtma bekor qilingan. Yangi buyurtma bering.", "Заказ отменён. Оформите новый заказ.");
        put("Subscription is not payable", "Bu tarifni hozir to‘lab bo‘lmaydi.", "Этот тариф сейчас нельзя оплатить.");
        put("Order is no longer payable", "Buyurtmani endi to‘lab bo‘lmaydi (bekor qilingan yoki muddati o‘tgan). Yangi buyurtma bering.", "Заказ больше нельзя оплатить (отменён или истёк срок). Оформите новый.");
        put("Order has no products", "Buyurtmada mahsulot yo‘q.", "В заказе нет товаров.");
        put("Product is out of stock or quantity is insufficient", "Uzr, buyurtmadagi mahsulot tugadi yoki yetarli emas. Pul yechilmadi.", "Извините, товар закончился или его недостаточно. Деньги не списаны.");
        put("Order already has active payment transaction", "Bu buyurtma uchun to‘lov allaqachon boshlangan. Bir necha daqiqadan keyin qayta urinib ko‘ring.", "Оплата этого заказа уже начата. Повторите через несколько минут.");
        put("Subscription already has active payment transaction", "Bu tarif uchun to‘lov allaqachon boshlangan. Bir necha daqiqadan keyin qayta urinib ko‘ring.", "Оплата тарифа уже начата. Повторите через несколько минут.");
        put("Transaction not found", "To‘lov topilmadi. Ilovaga qaytib, qayta urinib ko‘ring.", "Платёж не найден. Вернитесь в приложение и повторите.");
        put("Transaction cannot be performed", "To‘lovni bajarib bo‘lmadi (vaqti o‘tgan yoki bekor qilingan). Pul yechilmadi. Qayta urinib ko‘ring.", "Платёж не выполнен (истёк срок или отменён). Деньги не списаны. Повторите.");
        put("Transaction cannot be cancelled", "To‘lovni bekor qilib bo‘lmaydi.", "Платёж нельзя отменить.");
    }

    /** {"ru","uz","en"} for a known message; an unknown one keeps its English text in all three. */
    public static Map<String,String> of(String english) {
        String[] t = TEXT.getOrDefault(english, new String[]{english, english, english});
        var out = new LinkedHashMap<String,String>();
        out.put("ru", t[1]); out.put("uz", t[0]); out.put("en", t[2]);
        return out;
    }
}
