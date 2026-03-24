package com.measim.service.reserve;

import com.measim.model.economy.CommodityReserve;

public interface ReserveService {
    void initializeReserve();
    void gmManageReserve(int currentTick);
    double reserveRatio();
    double reserveValue();
    CommodityReserve getReserve();
}
