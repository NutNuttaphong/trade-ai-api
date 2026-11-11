package com.trade.stocks.service;

import com.trade.stocks.entity.PortfolioItem;
import com.trade.stocks.entity.Stock;
import com.trade.stocks.entity.User;
import com.trade.stocks.repository.PortfolioItemRepository;
import com.trade.stocks.repository.StockRepository;
import com.trade.stocks.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AiChatService {

    private final StockRepository stockRepository;
    private final PortfolioItemRepository portfolioRepository;
    private final UserRepository userRepository;

    public AiChatService(StockRepository stockRepository,
                          PortfolioItemRepository portfolioRepository,
                          UserRepository userRepository) {
        this.stockRepository = stockRepository;
        this.portfolioRepository = portfolioRepository;
        this.userRepository = userRepository;
    }

    public String generateResponse(Long userId, String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            return "ยินดีรับฟังเสมอครับ! มีอะไรเกี่ยวกับการลงทุนหรือหุ้นตัวไหนที่อยากคุยกัน พิมพ์มาได้เลยนะครับ 😊";
        }

        String lowerPrompt = prompt.toLowerCase();
        User user = (userId != null) ? userRepository.findById(userId).orElse(null) : null;
        String userName = (user != null && user.getUsername() != null) ? user.getUsername() : "คุณ";

        // 1. Casual Greetings & Friendly Chit-Chat (สวัสดี, ดีครับ, หวัดดี, hi, hello, สบายดีไหม, ทำอะไรได้บ้าง)
        if (lowerPrompt.matches(".*(สวัสดี|หวัดดี|ดีครับ|ดีค่ะ|ดีจ้า|ฮัลโหล|hello|hi|สบายดี|เป็นใคร|ชื่ออะไร|ทำอะไรได้).*")) {
            return String.format(
                "สวัสดีครับคุณ %s! ยินดีที่ได้คุยกันนะครับ 😊\n\n" +
                "ผมเป็นเพื่อนคู่คิดด้านการลงทุนของคุณ พร้อมช่วยดูความเคลื่อนไหวของตลาดสดๆ วิเคราะห์แนวโน้มหุ้น เช็คสุขภาพพอร์ต หรือช่วยคำนวณราคาสำหรับตั้ง Auto-Trade ได้ทันทีครับ\n\n" +
                "วันนี้มีหุ้นตัวไหนที่คุณกำลังสนใจเป็นพิเศษ หรืออยากให้ช่วยเช็คพอร์ตวันนี้ บอกผมได้เลยนะคร้าบ!",
                userName
            );
        }

        // 2. Stock Recommendation & Market Sentiment ("แนะนำหุ้น", "ซื้อตัวไหนดี", "เล่นตัวไหนดี", "ตลาดวันนี้", "มีอะไรน่าสนใจ", "แนะนำหน่อย")
        if (lowerPrompt.contains("แนะนำหุ้น") || lowerPrompt.contains("ซื้อตัวไหน") || lowerPrompt.contains("เล่นตัวไหน") ||
            lowerPrompt.contains("ตัวไหนดี") || lowerPrompt.contains("น่าซื้อ") || lowerPrompt.contains("น่าสนใจ") ||
            lowerPrompt.contains("ตลาดวันนี้") || lowerPrompt.contains("ตลาดเป็นไง") || lowerPrompt.contains("แนะนำหน่อย") ||
            lowerPrompt.contains("มีหุ้นอะไร") || lowerPrompt.contains("หุ้นเด่น")) {

            List<Stock> allStocks = stockRepository.findAll();
            if (allStocks.isEmpty()) {
                return "ตอนนี้ระบบกำลังรอข้อมูลราคาหุ้นจากกระดานสักครู่นะครับ รบกวนลองใหม่อีกครั้งในไม่กี่วินาทีครับ!";
            }

            // Sort by change percent descending
            List<Stock> sortedStocks = allStocks.stream()
                .sorted(Comparator.comparingDouble(Stock::getChangePercent).reversed())
                .collect(Collectors.toList());

            Stock topGainer = sortedStocks.get(0);
            Stock topDip = sortedStocks.get(sortedStocks.size() - 1);

            StringBuilder rec = new StringBuilder();
            rec.append(String.format("ยินดีเลยครับคุณ %s! มาส่องโอกาสในตลาดตอนนี้กันครับ 🚀\n\n", userName));

            if (topGainer.getChangePercent() > 0) {
                rec.append(String.format("🔥 **สายเก็งกำไรตามเทรนด์ (Momentum):**\n" +
                    "ขอแนะนำ **%s (%s)** เลยครับ วันนี้มาแรงสุดในกระดาน วิ่งขึ้นไปแล้ว +%.2f%% อยู่ที่ราคา **$%s** กำลังมีแรงซื้อดันราคาต่อเนื่อง เหมาะกับการเก็งกำไรระยะสั้นตามรอบครับ\n\n",
                    topGainer.getSymbol(), topGainer.getName(), topGainer.getChangePercent(), topGainer.getCurrentPrice()));
            }

            if (topDip.getChangePercent() < 0) {
                rec.append(String.format("💎 **สายสะสมของดีราคาถูก (Dip Buying / DCA):**\n" +
                    "ตอนนี้ **%s (%s)** มีการย่อตัวลงมา %.2f%% อยู่ที่ราคา **$%s** ถือเป็นโอกาสที่ดีในการทยอยตั้ง Auto-BUY สะสมต้นทุนต่ำ เพื่อรอการดีดตัวกลับครับ\n\n",
                    topDip.getSymbol(), topDip.getName(), topDip.getChangePercent(), topDip.getCurrentPrice()));
            }

            if (user != null) {
                rec.append(String.format("💡 ตอนนี้คุณมีเงินสดในมือ **$%s** พร้อมเข้าทำกำไรได้สบายๆ เลยครับ\n\n", user.getBalance()));
            }

            rec.append("คุณชอบแนวซิ่งตามเทรนด์ หรือเน้นสะสมตัวที่ย่อลงมาดีครับ? บอกชื่อหุ้นมาได้เลย เดี๋ยวผมช่วยคำนวณราคาเข้าซื้อให้ครับ!");
            return rec.toString();
        }

        // 3. Stock Comparison Analysis (e.g., "เปรียบเทียบ AAPL กับ MSFT", "TSLA vs NVDA")
        if (lowerPrompt.contains("เปรียบเทียบ") || lowerPrompt.contains("เทียบ") || lowerPrompt.contains("vs") || lowerPrompt.contains("compare")) {
            List<Stock> matchedStocks = new ArrayList<>();
            for (Stock s : stockRepository.findAll()) {
                if (lowerPrompt.contains(s.getSymbol().toLowerCase()) || lowerPrompt.contains(s.getName().toLowerCase())) {
                    matchedStocks.add(s);
                }
            }

            if (matchedStocks.size() >= 2) {
                Stock s1 = matchedStocks.get(0);
                Stock s2 = matchedStocks.get(1);

                Stock winner = s1.getChangePercent() >= s2.getChangePercent() ? s1 : s2;
                Stock other = s1.getChangePercent() >= s2.getChangePercent() ? s2 : s1;

                return String.format(
                    "มาจับคู่ชนระหว่าง **%s** กับ **%s** ให้เห็นภาพชัดๆ กันครับ! 🥊\n\n" +
                    "- **%s (%s):** ราคา $%s (ความเคลื่อนไหว: %s%.2f%%)\n" +
                    "- **%s (%s):** ราคา $%s (ความเคลื่อนไหว: %s%.2f%%)\n\n" +
                    "🎯 **มุมมองเปรียบเทียบ:**\n" +
                    "ชั่วโมงนี้ **%s** ค่อนข้างโดดเด่นและมีโมเมนตัมนำหน้า %s อยู่ครับ หากคุณเน้นรอบสั้นเพื่อทำกำไรไว ตัวนี้น่าจับตามาก แต่ถ้ามองหาตัวที่ราคายังไม่ไล่แรงและสะสมได้สบายใจ อีกตัวก็น่าสนใจไม่แพ้กันครับ\n\n" +
                    "คุณสนใจอยากตั้ง Auto-Trade ดักตัวไหนไว้ก่อนไหมครับ เดี๋ยวผมช่วยเลือกระดับราคาให้!",
                    s1.getSymbol(), s2.getSymbol(),
                    s1.getSymbol(), s1.getName(), s1.getCurrentPrice(), s1.getChangePercent() >= 0 ? "+" : "", s1.getChangePercent(),
                    s2.getSymbol(), s2.getName(), s2.getCurrentPrice(), s2.getChangePercent() >= 0 ? "+" : "", s2.getChangePercent(),
                    winner.getSymbol(), other.getSymbol()
                );
            }
        }

        // 4. Comprehensive Portfolio Health Check
        if (lowerPrompt.contains("พอร์ต") || lowerPrompt.contains("portfolio") || lowerPrompt.contains("ถือครอง") || lowerPrompt.contains("หุ้นที่มี") || lowerPrompt.contains("กำไร") || lowerPrompt.contains("ขาดทุน")) {
            if (user == null) return "ไม่สามารถดึงข้อมูลบัญชีผู้ใช้ได้ครับ";
            List<PortfolioItem> items = portfolioRepository.findByUserId(userId);
            if (items.isEmpty()) {
                return String.format(
                    "พอร์ตของคุณ %s ตอนนี้ยังว่างอยู่ครับ ยังไม่มีรายการถือครองหุ้นเลย 🌱\n\n" +
                    "แต่คุณมีเงินสดพร้อมลงทุนอยู่ถึง **$%s** แน่ะครับ! ถ้ายังไม่แน่ใจว่าจะเริ่มตัวไหนดี แนะนำลองดูหุ้นพื้นฐานแกร่งอย่าง **AAPL** หรือ **MSFT** เริ่มต้นทีละน้อยๆ ก่อนได้เลยครับ อยากให้ผมช่วยดูตัวไหนบอกได้เลยนะ!",
                    userName, user.getBalance()
                );
            }

            BigDecimal totalCost = BigDecimal.ZERO;
            BigDecimal totalMarketValue = BigDecimal.ZERO;
            StringBuilder holdingsText = new StringBuilder();

            for (PortfolioItem item : items) {
                Stock liveStock = stockRepository.findBySymbol(item.getSymbol()).orElse(null);
                BigDecimal currentPrice = liveStock != null ? liveStock.getCurrentPrice() : item.getAveragePrice();
                BigDecimal cost = item.getAveragePrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                BigDecimal value = currentPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
                BigDecimal pnl = value.subtract(cost);
                double pnlPct = cost.compareTo(BigDecimal.ZERO) > 0 ? pnl.divide(cost, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0.0;

                totalCost = totalCost.add(cost);
                totalMarketValue = totalMarketValue.add(value);

                String pnlIcon = pnl.compareTo(BigDecimal.ZERO) >= 0 ? "🟢" : "🔴";
                holdingsText.append(String.format(
                    "- %s **%s**: ถืออยู่ %d หุ้น (ทุนเฉลี่ย $%s | ราคาตลาด $%s) -> P&L: %s$%s (%.2f%%)\n",
                    pnlIcon, item.getSymbol(), item.getQuantity(), item.getAveragePrice(), currentPrice,
                    pnl.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "", pnl.setScale(2, RoundingMode.HALF_UP), pnlPct
                ));
            }

            BigDecimal totalPnl = totalMarketValue.subtract(totalCost);
            double totalPnlPct = totalCost.compareTo(BigDecimal.ZERO) > 0 ? totalPnl.divide(totalCost, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0.0;

            String feeling = totalPnl.compareTo(BigDecimal.ZERO) >= 0
                ? "เยี่ยมมากเลยครับ พอร์ตโดยรวมกำลังเขียวสดใสมีกำไร! 👏"
                : "ช่วงนี้ตลาดอาจจะย่อตัวบ้าง ไม่ต้องกังวลนะครับ ถือเป็นจังหวะบริหารจัดการพอร์ต! 💪";

            return String.format(
                "มาเช็คสุขภาพพอร์ตของคุณ %s กันครับ! 📈\n\n" +
                "%s\n\n" +
                "📊 **ภาพรวมตัวเลข:**\n" +
                "- **มูลค่าตลาดปัจจุบัน:** $%s (จากต้นทุนรวม $%s)\n" +
                "- **กำไร/ขาดทุนรวม:** %s$%s (%.2f%%)\n" +
                "- **เงินสดคงเหลือในมือ:** $%s\n\n" +
                "📋 **หุ้นที่ถือครอง:**\n%s\n" +
                "💡 **คำแนะนำจากใจ:**\n" +
                "หากตัวไหนกำไรถึงเป้า แนะนำแบ่งล็อกกำไรผ่านเมนู Trading ไว้บ้างนะครับ หรือหากอยากให้ช่วยตั้งจุดตัดขาดทุนตัวไหน แจ้งผมได้เลยครับ!",
                userName,
                feeling,
                totalMarketValue.setScale(2, RoundingMode.HALF_UP),
                totalCost.setScale(2, RoundingMode.HALF_UP),
                totalPnl.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "",
                totalPnl.setScale(2, RoundingMode.HALF_UP),
                totalPnlPct,
                user.getBalance(),
                holdingsText.toString()
            );
        }

        // 5. Auto-trade strategy & recommendation queries
        if (lowerPrompt.contains("auto") || lowerPrompt.contains("อัตโนมัติ") || lowerPrompt.contains("บอท") || lowerPrompt.contains("ตั้งค่า") || lowerPrompt.contains("กลยุทธ์")) {
            Stock targetStock = null;
            for (Stock s : stockRepository.findAll()) {
                if (lowerPrompt.contains(s.getSymbol().toLowerCase()) || lowerPrompt.contains(s.getName().toLowerCase())) {
                    targetStock = s;
                    break;
                }
            }

            if (targetStock != null) {
                BigDecimal buyTrigger = targetStock.getCurrentPrice().multiply(BigDecimal.valueOf(0.97)).setScale(2, RoundingMode.HALF_UP);
                BigDecimal sellTrigger = targetStock.getCurrentPrice().multiply(BigDecimal.valueOf(1.03)).setScale(2, RoundingMode.HALF_UP);

                return String.format(
                    "ยินดีเลยครับ! ถ้าจะตั้ง Auto-Trade สำหรับ **%s (%s)** 🎯\n\n" +
                    "ตอนนี้ราคาอยู่ที่ **$%s** ครับ ผมคำนวณจุดเข้าออกที่น่าสนใจให้ตามนี้เลย:\n\n" +
                    "🟢 **Auto-BUY (ดักช้อนซื้อ):** ตั้งไว้ที่ **$%s** (เผื่อราคาย่อลงมา -3%% ได้ของถูกตามแนวรับ)\n" +
                    "🔴 **Auto-SELL (ขายล็อกกำไร):** ตั้งไว้ที่ **$%s** (เมื่อราคาดีดตัวขึ้น +3%% เข้าเป้าแนวต้าน)\n\n" +
                    "ระบบเรามีบอทหลังบ้านคอยเช็คให้ทุก 1.5 วินาที แม้คุณปิดเว็บไปก็ไม่พลาดจังหวะแน่นอนครับ สะดวกและปลอดภัยมากๆ!",
                    targetStock.getSymbol(), targetStock.getName(),
                    targetStock.getCurrentPrice(), buyTrigger, sellTrigger
                );
            }

            return "ระบบ **Auto-Trading** ของเราช่วยให้การลงทุนง่ายขึ้นเยอะเลยครับ ไม่ต้องนั่งจ้องกราฟทั้งวัน! 🤖\n\n" +
                    "เราสามารถตั้งกฎไว้ล่วงหน้าได้ 2 แบบหลักๆ ครับ:\n" +
                    "1. **Auto-BUY:** ตั้งราคาดักซื้อตอนหุ้นย่อตัวลงมาแตะราคาที่ต้องการ\n" +
                    "2. **Auto-SELL:** ตั้งราคาขายเพื่อล็อกกำไร หรือตัดขาดทุนอัตโนมัติ\n\n" +
                    "คุณเล็งหุ้นตัวไหนไว้ไหมครับ? ลองพิมพ์มาได้เลย เช่น *'ช่วยตั้ง auto TSLA'* เดี๋ยวผมช่วยคำนวณราคาที่เหมาะสมให้ทันทีครับ!";
        }

        // 6. Specific Stock Deep Dive
        for (Stock stock : stockRepository.findAll()) {
            if (lowerPrompt.contains(stock.getSymbol().toLowerCase()) || lowerPrompt.contains(stock.getName().toLowerCase())) {
                boolean isBullish = stock.getChangePercent() >= 0;
                String mood = isBullish ? "กำลังมีแรงส่งในทิศทางขาขึ้น (Bullish) สวยงามเลยครับ 🟢" : "ช่วงนี้มีการพักฐานย่อตัวลงมา (Pullback) เล็กน้อยครับ 🔴";
                
                String advice;
                if (stock.getChangePercent() >= 2.0) {
                    advice = "ราคาพุ่งขึ้นมาเร็วมาก ถ้ามีของอยู่แล้วอาจจะพิจารณาแบ่งขายทำกำไรบางส่วน แต่ถ้าจะเข้าใหม่ ควรรอย่อตัวอีกนิดเพื่อความปลอดภัยครับ";
                } else if (stock.getChangePercent() <= -2.0) {
                    advice = "ราคาย่อตัวลงมาค่อนข้างลึก หากชอบพื้นฐานตัวนี้ ถือเป็นจังหวะที่ดีในการทยอยตั้ง Auto-BUY สะสมของต้นทุนต่ำครับ";
                } else {
                    advice = "ราคาแกว่งตัวในกรอบสร้างฐาน เหมาะกับการตั้งกรอบราคาซื้อขาย (Grid Trading) ทำกำไรตามรอบครับ";
                }

                BigDecimal estimatedSupport = stock.getCurrentPrice().multiply(BigDecimal.valueOf(0.98)).setScale(2, RoundingMode.HALF_UP);
                BigDecimal estimatedResistance = stock.getCurrentPrice().multiply(BigDecimal.valueOf(1.02)).setScale(2, RoundingMode.HALF_UP);

                return String.format(
                    "มาดู **%s (%s)** กันครับ! 🔍\n\n" +
                    "ราคาล่าสุดอยู่ที่ **$%s** (%s%.2f%%) %s\n\n" +
                    "📌 **แนวรับ-แนวต้านที่น่าจับตา:**\n" +
                    "- แนวรับ: **$%s**\n" +
                    "- แนวต้าน: **$%s**\n\n" +
                    "💡 **มุมมองการเทรด:**\n" +
                    "%s\n\n" +
                    "คุณมีแผนจะเข้าซื้อตัวนี้ หรือมีของอยู่ในมือแล้วอยากให้ช่วยดูจุดขายไหมครับ คุยกันได้เลยนะครับ!",
                    stock.getSymbol(), stock.getName(),
                    stock.getCurrentPrice(),
                    isBullish ? "+" : "", stock.getChangePercent(),
                    mood,
                    estimatedSupport, estimatedResistance,
                    advice
                );
            }
        }

        // 7. Psychological / Emotional Support (ติดดอย, ขาดทุน, เครียด, ทำไงดี, ขายดีไหม)
        if (lowerPrompt.contains("ติดดอย") || lowerPrompt.contains("ดอย") || lowerPrompt.contains("เครียด") ||
            lowerPrompt.contains("ทำไงดี") || lowerPrompt.contains("ขายดีไหม") || lowerPrompt.contains("ขาดทุนหนัก")) {
            return String.format(
                "เข้าใจความรู้สึกเลยครับคุณ %s การลงทุนย่อมมีจังหวะที่ตลาดไม่เป็นใจ แต่ไม่ต้องเครียดไปนะครับ ค่อยๆ วางแผนไปด้วยกัน! 🤝\n\n" +
                "ลองพิจารณา 3 ข้อง่ายๆ นี้ดูครับ:\n" +
                "1. **เช็คพื้นฐาน:** หุ้นที่เราถือพื้นฐานทางธุรกิจยังดีอยู่ไหม ถ้ายังดี การย่อตัวก็มักจะเป็นเรื่องชั่วคราว\n" +
                "2. **แบ่งไม้และจุดคัท:** ถ้าหลุดแนวรับสำคัญ การยอมถอยบางส่วน (Stop Loss) จะช่วยรักษาเงินต้นไว้สร้างโอกาสใหม่ได้ดีกว่า\n" +
                "3. **สภาพคล่อง:** เงินสดในมือมีความสำคัญเสมอ อย่าเพิ่งรีบถัวถ้ากราฟยังไม่สร้างฐาน\n\n" +
                "คุณติดหุ้นตัวไหนอยู่เป็นพิเศษไหมครับ บอกชื่อหุ้นและทุนเฉลี่ยมาได้เลยนะ เดี๋ยวผมช่วยเปิดกราฟดูจังหวะแก้เกมให้ครับ!",
                userName
            );
        }

        // 8. General Friendly Fallback (ไม่มีบล็อกตายตัว หรือ plattle แข็งๆ)
        return String.format(
            "ยินดีรับฟังและคุยกันเลยครับคุณ %s! 😊\n\n" +
            "ผมคอยติดตามตลาดสดๆ ในระบบให้คุณตลอดเวลา อยากให้ผมช่วยเช็คหุ้นตัวไหน (เช่น AAPL, NVDA, TSLA) หรืออยากปรึกษาเรื่องพอร์ตวันนี้ บอกมาได้เลยนะครับ คุยกันสบายๆ ได้เลย!",
            userName
        );
    }
}
