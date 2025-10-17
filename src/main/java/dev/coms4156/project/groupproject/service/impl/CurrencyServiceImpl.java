package dev.coms4156.project.groupproject.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.coms4156.project.groupproject.dto.CurrencyResponse;
import dev.coms4156.project.groupproject.entity.Currency;
import dev.coms4156.project.groupproject.mapper.CurrencyMapper;
import dev.coms4156.project.groupproject.service.CurrencyService;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class CurrencyServiceImpl extends ServiceImpl<CurrencyMapper, Currency> implements CurrencyService {

    @Override
    public CurrencyResponse getAllCurrencies() {
        return new CurrencyResponse(list().stream()
                .map(c -> new CurrencyResponse.CurrencyItem(c.getCode(), c.getExponent()))
                .collect(Collectors.toList()));
    }
}
