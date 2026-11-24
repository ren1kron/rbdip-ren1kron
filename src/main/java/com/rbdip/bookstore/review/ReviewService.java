package com.rbdip.bookstore.review;

import com.rbdip.bookstore.purchase.PurchaseHistory;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReviewService {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviews;
    private final PurchaseHistory purchases;

    public ReviewService(ReviewRepository reviews, PurchaseHistory purchases) {
        this.reviews = reviews;
        this.purchases = purchases;
    }

    public Review addReview(Long productId, String authorName, Integer rating, String comment) {
        LOG.atDebug().setMessage("Review for product {} has verified purchase {}").addArgument(productId)
                .addArgument(() -> authorName != null && purchases.hasPurchased(authorName, productId)).log();
        return reviews.save(new Review(productId, authorName == null ? "anonymous" : authorName, rating, comment));
    }

    public List<Review> listReviews(Long productId) {
        return reviews.findByProductId(productId);
    }
}
