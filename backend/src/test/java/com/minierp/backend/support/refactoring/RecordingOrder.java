package com.minierp.backend.support.refactoring;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
/** Only recording runs: choose a reproducible entry order for known outcome-dependent races.
* Default runs never install this filter and continue exercising real concurrent requests.
*/
final class RecordingOrder implements Filter {
    static final String POLICY = "ordered-outcome-dependent-http-races-v2";
    private static final Map<String, Integer> BATCHES = Map.of(
            "M2DocumentIntegrationTest#concurrentApprovalAndWithdrawalHaveOnlyOneWinner()", 2,
            "M2DocumentIntegrationTest#concurrentSubmissionsCannotSpendSameRemainingDays()", 2,
            "M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()", 8,
            "M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()",
                    2, "M4BoardConcurrencyTest#projectLockPreventsWriteSkewInTenConcurrentCompletions()", 2,
            "M4BoardConcurrencyTest#gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted()", 2);
    private final Map<String, List<Ticket>> waiting = new HashMap<>();
    private static final class Ticket {
        final String path;
        final CountDownLatch ready = new CountDownLatch(1);
        Ticket next;
        Ticket(String path) {
            this.path = path;
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException {
        String test = SafetyNetRun.workerTest();
        String shortName = test == null ? "" : test.substring(test.lastIndexOf('.') + 1);
        Integer size = BATCHES.get(shortName);
        if (size == null) {
            chain.doFilter(request, response);
            return;
        }
        Ticket ticket = new Ticket(
                ((HttpServletRequest) request).getMethod() + " " + ((HttpServletRequest) request).getRequestURI());
        synchronized (waiting) {
            List<Ticket> group = waiting.computeIfAbsent(test, key -> new ArrayList<>());
            group.add(ticket);
            if (group.size() == size) {
                group.sort(Comparator.comparing(t -> t.path));
                waiting.remove(test);
                // M3 first races identical adjustments; only its final submit/adjust pair needs ordering.
                if (group.stream().map(t -> t.path).distinct().count() == 1) {
                    group.forEach(t -> t.ready.countDown());
                } else {
                    for (int i = 0; i < group.size() - 1; i++) {
                        group.get(i).next = group.get(i + 1);
                    }
                    group.get(0).ready.countDown();
                }
            }
        }
        try {
            if (!ticket.ready.await(20, TimeUnit.SECONDS)) {
                throw new ServletException("Recording order timed out: " + test);
            }
            chain.doFilter(request, response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServletException("Recording order interrupted: " + test, exception);
        } finally {
            if (ticket.next != null) {
                ticket.next.ready.countDown();
            }
        }
    }
}
