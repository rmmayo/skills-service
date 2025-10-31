/*
 * Copyright 2020 SkillTree
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import ProjectService from '@/components/projects/ProjectService';
import SubjectsService from '@/components/subjects/SubjectsService';
import SkillsService from '@/components/skills/SkillsService';
import BadgesService from '@/components/badges/BadgesService';
import InputSanitizer from "@/components/utils/InputSanitizer.js";

const handleError = (error, context) => {
    console.error(`Error ${context}:`, error);
    throw error;
};

const createProject = async (data) => {
    try {
        const project = {
            projectId: InputSanitizer.sanitize(data.project.id),
            name: InputSanitizer.sanitize(data.project.name),
            description: data.project.description
        };
        await ProjectService.saveProject(project);
        return project.projectId;
    } catch (error) {
        handleError(error, 'creating project');
    }
};

const createSubject = async (projectId, subjectData) => {
    try {
        const subject = {
            projectId: projectId,
            subjectId: subjectData.id,
            name: subjectData.name,
            description: subjectData.description,
            iconClass: getIconClass(subjectData.icon),
            isEdit: false
        };
        await SubjectsService.saveSubject(subject);
        return subjectData.id;
    } catch (error) {
        handleError(error, `creating subject ${subjectData.name}`);
    }
};

const createSkill = async (projectId, subjectId, skillData) => {
    try {
        const skill = {
            projectId: projectId,
            subjectId: subjectId,
            skillId: skillData.skillId,
            name: skillData.name,
            description: skillData.description,
            pointIncrement: skillData.pointIncrement,
            numPerformToCompletion: skillData.numOccurrencesToCompletion,
            selfReportingType: /(HonorSystem|Approval)/.test(skillData.selfReporting) ? skillData.selfReporting : 'Approval',
            iconClass: getIconClass(skillData.icon),
            type: 'Skill',
            isEdit: false
        };
        await SkillsService.saveSkill(skill);
        return skillData.skillId;
    } catch (error) {
        handleError(error, `creating skill ${skillData.name}`);
    }
};

const createBadge = async (projectId, badgeData) => {
    try {
        const badge = {
            projectId: projectId,
            badgeId: badgeData.id,
            name: badgeData.name,
            description: badgeData.description,
            iconClass: getIconClass(badgeData.icon),
            enabled: true,
            isEdit: false,
            skillIds: badgeData.skillIds
        };
        await BadgesService.saveBadge(badge);
    } catch (error) {
        handleError(error, `creating badge ${badgeData.name}`);
    }
};

const getIconClass = (iconClass) => {
    if (!iconClass) {
        return 'fas fa-graduation-cap'
    }
    if (/^(fas |fa-solid | fa-solid-)/.test(iconClass)) {
        return iconClass;
    }
    return `fas fa-solid ${iconClass}`;
}

export default {
    async generateProject(data) {
        try {
            // Create project
            const projectId = await createProject(data);

            // Create subjects and skills
            for (const subject of data.subjects) {
                const subjectId = await createSubject(projectId, subject);

                // Create skills for this subject
                for (const skill of subject.skills) {
                    await createSkill(projectId, subjectId, skill);
                }
            }

            // Create badges
            for (const badge of data.badges) {
                await createBadge(projectId, badge);
            }

            return ProjectService.getProject(projectId)
        } catch (error) {
            console.error('Error in createAll:', error);
        }
    }
}