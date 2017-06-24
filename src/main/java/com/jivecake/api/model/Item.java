package com.jivecake.api.model;

import java.util.Date;
import java.util.List;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Field;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.Indexes;
import org.mongodb.morphia.utils.IndexType;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jivecake.api.serializer.ObjectIdSerializer;

@Indexes({
    @Index(fields = @Field(value = "name", type = IndexType.TEXT))
})
@Entity
public class Item {
    @Id
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId id;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId eventId;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId organizationId;
    public String name;
    public String description;
    public Integer totalAvailible;
    public Integer maximumPerUser;
    public double amount;
    public List<ItemTimeAmount> timeAmounts;
    public List<ItemCountAmount> countAmounts;
    public int status;
    public Date timeStart;
    public Date timeEnd;
    public Date timeUpdated;
    public Date timeCreated;
    public Date lastActivity;

    public double getDerivedAmount(long numberOfTransactions, Date date) {
        double result = this.amount;

        if (this.timeAmounts != null) {
            result = this.getDerivedAmountFromTime(date);
        } else if (this.countAmounts != null) {
            result = this.getDerivedAmountFromCounts(numberOfTransactions);
        }

        return result;
    }

    public double getDerivedAmountFromCounts(long numberOfTransactions) {
        double result = this.amount;

        for (int index = this.countAmounts.size() - 1; index > -1; index--) {
            ItemCountAmount countAmount = this.countAmounts.get(index);

            if (numberOfTransactions >= countAmount.count) {
                result = countAmount.amount;
                break;
            }
        }

        return result;
    }

    public Double getDerivedAmountFromTime(Date date) {
        Double result = this.amount;

        for (int index = this.timeAmounts.size() - 1; index > -1; index--) {
            ItemTimeAmount timeAmount = this.timeAmounts.get(index);

            if (date.after(timeAmount.after)) {
                result = timeAmount.amount;
                break;
            }
        }

        return result;
    }
}