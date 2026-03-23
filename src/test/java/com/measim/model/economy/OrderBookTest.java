package com.measim.model.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private final ItemType item = ItemType.of(ProductType.FOOD);

    @Test void matchesWhenBuyPriceExceedsSell() {
        OrderBook book = new OrderBook(item);
        book.submit(new Order("buyer1", item, 5, 10.0, Order.OrderSide.BUY, 0));
        book.submit(new Order("seller1", item, 5, 8.0, Order.OrderSide.SELL, 0));
        var trades = book.match();
        assertEquals(1, trades.size());
        assertEquals(5, trades.getFirst().quantity());
        assertEquals(9.0, trades.getFirst().pricePerUnit());
    }

    @Test void noMatchWhenBuyBelowSell() {
        OrderBook book = new OrderBook(item);
        book.submit(new Order("buyer1", item, 5, 5.0, Order.OrderSide.BUY, 0));
        book.submit(new Order("seller1", item, 5, 10.0, Order.OrderSide.SELL, 0));
        assertTrue(book.match().isEmpty());
    }

    @Test void partialFillCreatesResidualOrder() {
        OrderBook book = new OrderBook(item);
        book.submit(new Order("buyer1", item, 10, 10.0, Order.OrderSide.BUY, 0));
        book.submit(new Order("seller1", item, 3, 8.0, Order.OrderSide.SELL, 0));
        var trades = book.match();
        assertEquals(1, trades.size());
        assertEquals(3, trades.getFirst().quantity());
        assertEquals(1, book.buyDepth());
    }

    @Test void multipleTradesInOrder() {
        OrderBook book = new OrderBook(item);
        book.submit(new Order("buyer1", item, 5, 12.0, Order.OrderSide.BUY, 0));
        book.submit(new Order("buyer2", item, 5, 10.0, Order.OrderSide.BUY, 0));
        book.submit(new Order("seller1", item, 3, 8.0, Order.OrderSide.SELL, 0));
        book.submit(new Order("seller2", item, 3, 9.0, Order.OrderSide.SELL, 0));
        var trades = book.match();
        assertFalse(trades.isEmpty());
        assertTrue(trades.size() <= 3);
    }
}
