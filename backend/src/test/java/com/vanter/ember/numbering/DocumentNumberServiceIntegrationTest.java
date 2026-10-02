package com.vanter.ember.numbering;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
// Own context on purpose (the extra property changes the cache key): sharing the context with
// TableLinkConcurrencyIntegrationTest would stop that test from getting a freshly created (create-drop) schema.
@TestPropertySource(properties = {"ember.ratelimit.enabled=false", "ember.test.context=document-numbering"})
class DocumentNumberServiceIntegrationTest {

    @Autowired DocumentNumberService service;
    @Autowired DocumentCounterRepository counters;
    @Autowired RestaurantRepository restaurantRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = newRestaurant("el-pollo-loco-" + UUID.randomUUID());
    }

    private UUID newRestaurant(String slug) {
        return restaurantRepository.save(Restaurant.builder().name("Test").slug(slug).build()).getId();
    }

    @Test
    void numbersAreConsecutiveFromOne_andCarryThePrefixOfTheSlug() {
        IssuedNumber first = service.next(tenantId, DocumentSeries.BILL);
        IssuedNumber second = service.next(tenantId, DocumentSeries.BILL);

        assertThat(first.number()).isEqualTo(1);
        assertThat(first.code()).isEqualTo("ELPO-000001");
        assertThat(second.number()).isEqualTo(2);
        assertThat(second.code()).isEqualTo("ELPO-000002");
    }

    @Test
    void kitchenSeries_isIndependentFromBills() {
        service.next(tenantId, DocumentSeries.BILL);
        service.next(tenantId, DocumentSeries.BILL);

        IssuedNumber kds = service.next(tenantId, DocumentSeries.KDS);

        assertThat(kds.number()).isEqualTo(1);
        assertThat(kds.code()).isEqualTo("ELPO-KDS-000001");
    }

    @Test
    void tenants_doNotShareACounter() {
        UUID other = newRestaurant("burger-house-" + UUID.randomUUID());
        service.next(tenantId, DocumentSeries.BILL);
        service.next(tenantId, DocumentSeries.BILL);

        IssuedNumber fromOther = service.next(other, DocumentSeries.BILL);

        assertThat(fromOther.number()).isEqualTo(1);
        assertThat(fromOther.code()).isEqualTo("BURG-000001");
    }

    @Test
    void prefix_isFrozenAtFirstUse_soRenamingTheSlugNeverChangesIssuedCodes() {
        service.next(tenantId, DocumentSeries.BILL);
        Restaurant restaurant = restaurantRepository.findById(tenantId).orElseThrow();
        restaurant.setSlug("renamed-" + UUID.randomUUID());
        restaurantRepository.save(restaurant);

        IssuedNumber next = service.next(tenantId, DocumentSeries.BILL);

        assertThat(next.code()).isEqualTo("ELPO-000002");
    }

    @Test
    void concurrentIssuers_neverGetTheSameNumber_andLeaveNoGaps() throws Exception {
        int threads = 8;
        int perThread = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<Integer>>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                List<Integer> mine = new ArrayList<>();
                for (int i = 0; i < perThread; i++) {
                    mine.add(service.next(tenantId, DocumentSeries.BILL).number());
                }
                return mine;
            }));
        }
        start.countDown();
        Set<Integer> all = new TreeSet<>();
        int total = 0;
        for (Future<List<Integer>> f : futures) {
            List<Integer> mine = f.get();
            total += mine.size();
            all.addAll(mine);
        }
        pool.shutdown();

        assertThat(total).isEqualTo(threads * perThread);
        assertThat(all).hasSize(threads * perThread);
        assertThat(all.iterator().next()).isEqualTo(1);
        assertThat(((TreeSet<Integer>) all).last()).isEqualTo(threads * perThread);
        assertThat(counters.findById(new DocumentCounter.Key(tenantId, DocumentSeries.BILL)).orElseThrow()
                .getLastNumber()).isEqualTo(threads * perThread);
    }
}
