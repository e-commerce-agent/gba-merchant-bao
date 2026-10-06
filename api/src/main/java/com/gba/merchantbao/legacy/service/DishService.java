package com.gba.merchantbao.legacy.service;

import com.gba.merchantbao.legacy.dto.DishDTO;
import com.gba.merchantbao.legacy.dto.DishPageQueryDTO;
import com.gba.merchantbao.legacy.entity.Dish;
import com.gba.merchantbao.legacy.result.PageResult;
import com.gba.merchantbao.legacy.vo.DishVO;

import java.util.List;

public interface DishService {
    /**
     * 新增菜品和对应的口味
     * @param dishDTO
     */
    public void saveWithFlavor(DishDTO dishDTO);

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 菜品批量删除
     * @param ids
     */
    void deleteBatch(List<Long> ids);

    /**
     * 根据id查询菜品信息
     * @param id
     * @return
     */
    DishVO getByIdWithFlavor(Long id);

    /*
        * 修改菜品和对应的口味
     */
    void updateWithFlavor(DishDTO dishDTO);

    /**
     * 根据分类id查询菜品
     * @param categoryId
     * @return
     */
    List<Dish> list(Long categoryId);

    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    List<DishVO> listWithFlavor(Dish dish);
}
