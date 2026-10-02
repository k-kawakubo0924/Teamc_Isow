package com.teamc.isow.backend.master;

import com.teamc.isow.backend.common.AgeGroup;
import com.teamc.isow.backend.common.Gender;
import com.teamc.isow.backend.master.MastersResponse.EnumOption;
import com.teamc.isow.backend.master.MastersResponse.MasterOption;
import com.teamc.isow.backend.tag.TagRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterService {

    private final FashionCategoryRepository fashionCategoryRepository;
    private final BodyTypeRepository bodyTypeRepository;
    private final PersonalColorRepository personalColorRepository;
    private final TagRepository tagRepository;

    public MasterService(
            FashionCategoryRepository fashionCategoryRepository,
            BodyTypeRepository bodyTypeRepository,
            PersonalColorRepository personalColorRepository,
            TagRepository tagRepository) {
        this.fashionCategoryRepository = fashionCategoryRepository;
        this.bodyTypeRepository = bodyTypeRepository;
        this.personalColorRepository = personalColorRepository;
        this.tagRepository = tagRepository;
    }

    /** 選択肢の一覧。無効なもの・手入力のタグは含めない */
    @Transactional(readOnly = true)
    public MastersResponse getMasters() {
        return new MastersResponse(
                fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                        .map(MasterOption::from).toList(),
                bodyTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                        .map(MasterOption::from).toList(),
                personalColorRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                        .map(MasterOption::from).toList(),
                tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                        .map(MasterOption::from).toList(),
                Arrays.stream(AgeGroup.values())
                        .map(ageGroup -> new EnumOption(ageGroup.name(), ageGroup.getLabel())).toList(),
                Arrays.stream(Gender.values())
                        .map(gender -> new EnumOption(gender.name(), gender.getLabel())).toList());
    }
}
