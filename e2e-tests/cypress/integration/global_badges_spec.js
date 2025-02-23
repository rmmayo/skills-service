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
describe('Global Badges Tests', () => {

    beforeEach(() => {

        cy.logout();
        const supervisorUser = 'supervisor@skills.org';
        cy.register(supervisorUser, 'password');
        cy.login('root@skills.org', 'password');
        cy.request('PUT', `/root/users/${supervisorUser}/roles/ROLE_SUPERVISOR`);
        cy.logout();
        cy.login(supervisorUser, 'password');
    });

    it('Create badge with special chars', () => {

        const expectedId = 'LotsofspecialPcharsBadge';
        const providedName = "!L@o#t$s of %s^p&e*c/?#(i)a_l++_|}{P c'ha'rs";

        cy.intercept('GET', `/supervisor/badges`).as('getGlobalBadges');
        cy.intercept('PUT', `/supervisor/badges/${expectedId}`).as('postGlobalBadge');
        cy.intercept('GET', `/supervisor/badges/id/${expectedId}/exists`).as('idExists');
        cy.intercept('POST', '/supervisor/badges/name/exists').as('nameExists');
        cy.intercept('GET', '/app/userInfo/hasRole/ROLE_SUPERVISOR').as('checkSupervisorRole')

        cy.visit('/globalBadges');
        cy.wait('@getGlobalBadges');
        cy.wait('@checkSupervisorRole');

        cy.clickButton('Badge');

        cy.get('#badgeName').type(providedName);
        cy.wait('@nameExists');
        cy.clickSave();
        cy.wait('@idExists');
        cy.wait('@postGlobalBadge');

        cy.contains(`ID: ${expectedId}`);
    });

    it('name causes id to fail validation', () => {

        cy.intercept('GET', `/supervisor/badges`).as('getGlobalBadges');
        cy.intercept('POST', '/supervisor/badges/name/exists').as('nameExists');
        cy.intercept('GET', '/app/userInfo/hasRole/ROLE_SUPERVISOR').as('checkSupervisorRole');

        cy.visit('/globalBadges');
        cy.wait('@getGlobalBadges');
        cy.wait('@checkSupervisorRole');

        cy.clickButton('Badge');

        // name causes id to be too long
        const msg = 'Badge ID cannot exceed 50 characters';
        const validNameButInvalidId = Array(47).fill('a').join('');
        cy.get('[data-cy=badgeName]').click();
        cy.get('[data-cy=badgeName]').invoke('val', validNameButInvalidId).trigger('input');
        cy.get('[data-cy=idError]').contains(msg).should('be.visible');
        cy.get('[data-cy=saveBadgeButton]').should('be.disabled');
        cy.get('[data-cy=badgeName]').type('{backspace}{backspace}');
        cy.get('[data-cy=idError]').should('not.be.visible');
        cy.get('[data-cy=saveBadgeButton]').should('be.enabled');
    });



    it('Delete badge', () => {
        const expectedId = 'JustABadgeBadge';
        const providedName = "JustABadge";

        cy.request('PUT', `/supervisor/badges/${expectedId}`, {
            badgeId: expectedId,
            description: "",
            iconClass: 'fas fa-award',
            isEdit: false,
            name: providedName,
            originalBadgeId: ''
        });


        cy.intercept('GET', `/supervisor/badges`).as('getGlobalBadges');
        cy.intercept('GET', '/app/userInfo/hasRole/ROLE_SUPERVISOR').as('checkSupervisorRole')
        cy.intercept('DELETE', `/supervisor/badges/${expectedId}`).as('deleteGlobalBadge');

        cy.visit('/globalBadges');
        cy.wait('@getGlobalBadges');
        cy.wait('@checkSupervisorRole');

        cy.get('.card-body button.dropdown-toggle').click();
        cy.get('.card-body div.dropdown').contains('Delete').click();
        cy.get('.btn-danger').contains('YES, Delete It!').click();
        cy.wait('@deleteGlobalBadge');
        cy.contains('No Badges Yet').should('be.visible');
    });

    it('Add dependencies to badge', () => {
        //proj/subj/skill1
        cy.request('POST', '/app/projects/proj1', {
            projectId: 'proj1',
            name: "proj1"
        });
        cy.request('POST', '/admin/projects/proj1/subjects/subj1', {
            projectId: 'proj1',
            subjectId: 'subj1',
            name: "Subject 1"
        });
        cy.request('POST', `/admin/projects/proj1/subjects/subj1/skills/skill1`, {
            projectId: 'proj1',
            subjectId: 'subj1',
            skillId: 'skill1',
            name: `This is 1`,
            type: 'Skill',
            pointIncrement: 100,
            numPerformToCompletion: 5,
            pointIncrementInterval: 0,
            numMaxOccurrencesIncrementInterval: -1,
            version: 0,
        });
        //proj/subj/skill2
        cy.request('POST', '/app/projects/proj2', {
            projectId: 'proj2',
            name: "proj2"
        });
        cy.request('POST', '/admin/projects/proj2/subjects/subj1', {
            projectId: 'proj2',
            subjectId: 'subj1',
            name: "Subject 1"
        });
        cy.request('POST', `/admin/projects/proj2/subjects/subj1/skills/skill1`, {
            projectId: 'proj2',
            subjectId: 'subj1',
            skillId: 'skill1',
            name: `This is 1`,
            type: 'Skill',
            pointIncrement: 100,
            numPerformToCompletion: 5,
            pointIncrementInterval: 0,
            numMaxOccurrencesIncrementInterval: -1,
            version: 0,
        });

        const badgeId = 'a_badge';
        cy.request('PUT', `/supervisor/badges/${badgeId}`, {
            badgeId: badgeId,
            description: "",
            iconClass: 'fas fa-award',
            isEdit: false,
            name: 'A Badge',
            originalBadgeId: ''
        });

        cy.intercept('GET', '/supervisor/badges').as('getBadges');
        cy.intercept('GET', '/supervisor/projects/proj2/levels').as('getLevels');
        cy.intercept('GET', '/supervisor/badges/a_badge/projects/available').as('getAvailableLevels');

        cy.visit('/');
        cy.clickNav('Badges');
        cy.wait('@getBadges');
        cy.contains('Manage').click({force:true});
        cy.get('.multiselect__tags').click();
        cy.get('.multiselect__tags input').type('{enter}');
        cy.get('div.table-responsive').should('be.visible');
        cy.clickNav('Levels');
        cy.wait('@getAvailableLevels');

        cy.get('#skills-selector').first().click();
        cy.get('.multiselect__tags input').first().type('proj2{enter}');

        cy.get('#level-selector').last().click();
        cy.wait('@getLevels');
        cy.get('.multiselect__tags input').last().type('5{enter}');

        cy.contains('Add').click();
        cy.get('#simple-levels-table').should('be.visible');
    });

    it('Navigate to global badges menu entry', () => {

        cy.intercept('GET', `/supervisor/badges`).as('getGlobalBadges');

        cy.visit('/');
        cy.clickNav('Badges');
        cy.wait('@getGlobalBadges');
    });

    it('Cannot publish Global Badge with no Skills and no Levels', () => {
        const expectedId = 'TestBadgeBadge';
        cy.intercept('GET', `/supervisor/badges`).as('getGlobalBadges');
        cy.intercept('PUT', `/supervisor/badges/${expectedId}`).as('postGlobalBadge');
        cy.intercept('GET', `/supervisor/badges/id/${expectedId}/exists`).as('idExists');
        cy.intercept('POST', '/supervisor/badges/name/exists').as('nameExists');
        cy.intercept('GET', '/app/userInfo/hasRole/ROLE_SUPERVISOR').as('checkSupervisorRole');
        cy.intercept('GET', `/supervisor/badges/${expectedId}`).as('getExpectedBadge');

        cy.visit('/globalBadges');
        cy.wait('@getGlobalBadges');
        cy.wait('@checkSupervisorRole');

        cy.clickButton('Badge');

        cy.get('#badgeName').type('Test Badge');
        cy.wait('@nameExists');
        cy.clickSave();
        cy.wait('@postGlobalBadge');

        cy.clickNav('Badges');

        cy.contains('Test Badge').should('exist');
        cy.get('[data-cy=badgeStatus]').contains('Status: Disabled').should('exist');
        cy.get('[data-cy=goLive]').click();
        cy.contains('This Global Badge has no assigned Skills or Project Levels. A Global Badge cannot be published without at least one Skill or Project Level.').should('exist');
        cy.get('[data-cy=badgeStatus]').contains('Status: Disabled').should('exist');
    });

    it('Global Badge is disabled when created, can only be enabled once', () => {
        cy.request('POST', '/app/projects/proj1', {
            projectId: 'proj1',
            name: "proj1"
        });
        cy.request('POST', '/admin/projects/proj1/subjects/subj1', {
            projectId: 'proj1',
            subjectId: 'subj1',
            name: "Subject 1"
        });
        cy.request('POST', `/admin/projects/proj1/subjects/subj1/skills/skill1`, {
            projectId: 'proj1',
            subjectId: 'subj1',
            skillId: 'skill1',
            name: `This is 1`,
            type: 'Skill',
            pointIncrement: 100,
            numPerformToCompletion: 5,
            pointIncrementInterval: 0,
            numMaxOccurrencesIncrementInterval: -1,
            version: 0,
        });

        const expectedId = 'TestBadgeBadge';
        cy.intercept('GET', `/supervisor/badges`).as('getGlobalBadges');
        cy.intercept('PUT', `/supervisor/badges/${expectedId}`).as('postGlobalBadge');
        cy.intercept('GET', `/supervisor/badges/id/${expectedId}/exists`).as('idExists');
        cy.intercept('POST', '/supervisor/badges/name/exists').as('nameExists');
        cy.intercept('GET', '/app/userInfo/hasRole/ROLE_SUPERVISOR').as('checkSupervisorRole');
        cy.intercept('GET', `/supervisor/badges/${expectedId}`).as('getExpectedBadge');

        cy.visit('/globalBadges');
        cy.wait('@getGlobalBadges');
        cy.wait('@checkSupervisorRole');

        cy.clickButton('Badge');

        cy.get('#badgeName').type('Test Badge');
        cy.wait('@nameExists');
        cy.clickSave();
        cy.wait('@postGlobalBadge');

        cy.clickNav('Badges');
        cy.contains('Manage').click();
        cy.get('.multiselect__tags').click();
        cy.get('.multiselect__tags input').type('{enter}');
        cy.get('div.table-responsive').should('be.visible');
        cy.contains('GlobalBadges').click();

        cy.contains('Test Badge').should('exist');
        cy.get('[data-cy=badgeStatus]').contains('Status: Disabled').should('exist');
        cy.get('[data-cy=goLive]').click();
        cy.contains('Please Confirm!').should('exist');
        cy.contains('Yes, Go Live!').click();
        cy.wait('@postGlobalBadge');
        cy.wait('@getExpectedBadge');
        cy.wait('@getGlobalBadges');
        cy.contains('Test Badge');
        cy.get('[data-cy=badgeStatus]').contains('Status: Live').should('exist');
        cy.get('[data-cy=goLive]').should('not.exist');
    });

    it('Canceling go live dialog should leave global badge disabled', () => {
        cy.request('POST', '/app/projects/proj1', {
            projectId: 'proj1',
            name: "proj1"
        });
        cy.request('POST', '/admin/projects/proj1/subjects/subj1', {
            projectId: 'proj1',
            subjectId: 'subj1',
            name: "Subject 1"
        });
        cy.request('POST', `/admin/projects/proj1/subjects/subj1/skills/skill1`, {
            projectId: 'proj1',
            subjectId: 'subj1',
            skillId: 'skill1',
            name: `This is 1`,
            type: 'Skill',
            pointIncrement: 100,
            numPerformToCompletion: 5,
            pointIncrementInterval: 0,
            numMaxOccurrencesIncrementInterval: -1,
            version: 0,
        });

        const expectedId = 'TestBadgeBadge';
        cy.intercept('GET', `/supervisor/badges`).as('getGlobalBadges');
        cy.intercept('PUT', `/supervisor/badges/${expectedId}`).as('postGlobalBadge');
        cy.intercept('GET', `/supervisor/badges/id/${expectedId}/exists`).as('idExists');
        cy.intercept('POST', '/supervisor/badges/name/exists').as('nameExists');
        cy.intercept('GET', '/app/userInfo/hasRole/ROLE_SUPERVISOR').as('checkSupervisorRole');

        cy.visit('/globalBadges');
        cy.wait('@getGlobalBadges');
        cy.wait('@checkSupervisorRole');

        cy.clickButton('Badge');

        cy.get('#badgeName').type('Test Badge');
        cy.wait('@nameExists');
        cy.clickSave();
        cy.wait('@postGlobalBadge');

        cy.contains('Test Badge').should('exist');
        cy.contains('Manage').click();
        cy.get('.multiselect__tags').click();
        cy.get('.multiselect__tags input').type('{enter}');
        cy.get('div.table-responsive').should('be.visible');

        cy.contains('GlobalBadges').click();
        cy.wait('@getGlobalBadges');

        cy.contains('Test Badge').should('exist');
        cy.get('[data-cy=badgeStatus]').contains('Status: Disabled').should('exist');
        cy.get('[data-cy=goLive]').click();
        cy.contains('Please Confirm!').should('exist');
        cy.contains('Cancel').click();
        cy.contains('Test Badge');
        cy.get('[data-cy=badgeStatus]').contains('Status: Live').should('not.exist');
        cy.get('[data-cy=goLive]').should('exist');
    });

    it('Can add Skill and Level requirements to disabled Global Badge', () => {
        cy.intercept('GET', `/supervisor/badges`).as('getGlobalBadges');
        cy.intercept('PUT', `/supervisor/badges/ABadgeBadge`).as('postGlobalBadge');
        cy.intercept('GET', `/supervisor/badges/id/ABadgeBadge/exists`).as('idExists');
        cy.intercept('POST', '/supervisor/badges/name/exists').as('nameExists');
        cy.intercept('GET', '/supervisor/badges/ABadgeBadge/projects/available').as('availableProjects')
        cy.intercept('GET', '/supervisor/badges/ABadgeBadge/skills/available?query=').as('availableSkills')
        cy.intercept('GET', '/supervisor/badges/ABadgeBadge').as('badgeInfo')
        cy.intercept('GET', '/supervisor/projects/proj2/levels').as('proj2Levels');
        //proj/subj/skill1
        cy.request('POST', '/app/projects/proj1', {
            projectId: 'proj1',
            name: "proj1"
        });
        cy.request('POST', '/admin/projects/proj1/subjects/subj1', {
            projectId: 'proj1',
            subjectId: 'subj1',
            name: "Subject 1"
        });
        cy.request('POST', `/admin/projects/proj1/subjects/subj1/skills/skill1`, {
            projectId: 'proj1',
            subjectId: 'subj1',
            skillId: 'skill1',
            name: `This is 1`,
            type: 'Skill',
            pointIncrement: 100,
            numPerformToCompletion: 5,
            pointIncrementInterval: 0,
            numMaxOccurrencesIncrementInterval: -1,
            version: 0,
        });
        //proj/subj/skill2
        cy.request('POST', '/app/projects/proj2', {
            projectId: 'proj2',
            name: "proj2"
        });
        cy.request('POST', '/admin/projects/proj2/subjects/subj1', {
            projectId: 'proj2',
            subjectId: 'subj1',
            name: "Subject 1"
        });
        cy.request('POST', `/admin/projects/proj2/subjects/subj1/skills/skill1`, {
            projectId: 'proj2',
            subjectId: 'subj1',
            skillId: 'skill1',
            name: `This is 1`,
            type: 'Skill',
            pointIncrement: 100,
            numPerformToCompletion: 5,
            pointIncrementInterval: 0,
            numMaxOccurrencesIncrementInterval: -1,
            version: 0,
        });

        cy.visit('/');

        cy.clickNav('Badges');
        cy.wait('@getGlobalBadges');

        cy.clickButton('Badge');

        cy.get('#badgeName').type('A Badge');
        cy.wait('@nameExists');
        cy.clickSave();
        cy.wait('@idExists');
        cy.wait('@postGlobalBadge');

        cy.contains('A Badge').should('exist');
        cy.contains('Manage').click();

        cy.wait('@availableSkills');
        cy.get('.multiselect__tags').click();
        cy.get('.multiselect__tags input').type('{enter}');
        cy.get('div.table-responsive').should('be.visible');
        cy.clickNav('Levels');

        cy.wait('@availableProjects');
        cy.wait('@badgeInfo');

        cy.get('.multiselect__tags').first().click({force:true});
        cy.get('.multiselect__tags input').first().type('proj2{enter}');

        cy.wait('@proj2Levels');

        cy.get('.multiselect__tags').last().click({force: true});
        cy.get('.multiselect__tags input').last().type('5{enter}');

        cy.contains('Add').click();
        cy.get('#simple-levels-table').should('be.visible');

        cy.contains('.router-link-active', 'Badges').click();
        cy.wait('@getGlobalBadges');

        cy.contains('A Badge').should('exist');

        cy.get('[data-cy=badgeStatus]').contains('Status: Disabled').should('exist');
        cy.get('[data-cy=goLive]').click();
        cy.contains('Please Confirm!').should('exist');
        cy.contains('Yes, Go Live!').click();
        cy.wait('@getGlobalBadges');
        cy.contains('A Badge').should('exist');
        cy.get('[data-cy=badgeStatus]').contains('Status: Live').should('exist');
        cy.get('[data-cy=goLive]').should('not.exist');
    });

    it('Removing all skills should not cause published Global Badge to become disabled', () => {
        cy.intercept('GET', `/supervisor/badges`).as('getGlobalBadges');
        cy.intercept('PUT', `/supervisor/badges/ABadgeBadge`).as('postGlobalBadge');
        cy.intercept('GET', `/supervisor/badges/id/ABadgeBadge/exists`).as('idExists');
        cy.intercept('POST', '/supervisor/badges/name/exists').as('nameExists');
        //proj/subj/skill1
        cy.request('POST', '/app/projects/proj1', {
            projectId: 'proj1',
            name: "proj1"
        });
        cy.request('POST', '/admin/projects/proj1/subjects/subj1', {
            projectId: 'proj1',
            subjectId: 'subj1',
            name: "Subject 1"
        });
        cy.request('POST', `/admin/projects/proj1/subjects/subj1/skills/skill1`, {
            projectId: 'proj1',
            subjectId: 'subj1',
            skillId: 'skill1',
            name: `This is 1`,
            type: 'Skill',
            pointIncrement: 100,
            numPerformToCompletion: 5,
            pointIncrementInterval: 0,
            numMaxOccurrencesIncrementInterval: -1,
            version: 0,
        });
        //proj/subj/skill2
        cy.request('POST', '/app/projects/proj2', {
            projectId: 'proj2',
            name: "proj2"
        });
        cy.request('POST', '/admin/projects/proj2/subjects/subj1', {
            projectId: 'proj2',
            subjectId: 'subj1',
            name: "Subject 1"
        });
        cy.request('POST', `/admin/projects/proj2/subjects/subj1/skills/skill1`, {
            projectId: 'proj2',
            subjectId: 'subj1',
            skillId: 'skill1',
            name: `This is 1`,
            type: 'Skill',
            pointIncrement: 100,
            numPerformToCompletion: 5,
            pointIncrementInterval: 0,
            numMaxOccurrencesIncrementInterval: -1,
            version: 0,
        });

        cy.intercept('GET', '/supervisor/projects/proj2/levels').as('getLevels');
        cy.intercept('GET', '/supervisor/badges/ABadgeBadge/projects/available').as('getAvailableLevels');

        cy.visit('/');

        cy.clickNav('Badges');
        cy.wait('@getGlobalBadges');

        cy.clickButton('Badge');

        cy.get('#badgeName').type('A Badge');
        cy.wait('@nameExists');
        cy.clickSave();
        cy.wait('@idExists');
        cy.wait('@postGlobalBadge');

        cy.contains('A Badge').should('exist');
        cy.contains('Manage').click();
        //wahat to wait on....
        cy.get('.multiselect__tags').click();
        cy.get('.multiselect__tags input').type('{enter}');
        cy.get('div.table-responsive').should('be.visible');
        cy.clickNav('Levels');
        cy.wait('@getAvailableLevels');

        cy.get('.multiselect__tags').first().click({force: true});
        cy.get('.multiselect__tags input').first().type('proj2{enter}');

        cy.get('.multiselect__tags').last().click();
        cy.get('.multiselect__tags input').last().type('5{enter}');

        cy.contains('Add').click();
        cy.get('#simple-levels-table').should('be.visible');

        cy.contains('.router-link-active', 'Badges').click();
        cy.wait('@getGlobalBadges');

        cy.contains('A Badge').should('exist');

        cy.get('[data-cy=badgeStatus]').contains('Status: Disabled').should('exist');
        cy.get('[data-cy=goLive]').click();
        cy.contains('Please Confirm!').should('exist');
        cy.contains('Yes, Go Live!').click();
        cy.wait('@getGlobalBadges');
        cy.contains('A Badge').should('exist');
        cy.get('[data-cy=badgeStatus]').contains('Status: Live').should('exist');
        cy.get('[data-cy=goLive]').should('not.exist');

        cy.contains('Manage').click();
        cy.get('[data-cy=deleteSkill]').click();
        cy.contains('YES, Delete It!').click();
        cy.contains('.router-link-active', 'Badges').click();
        cy.get('[data-cy=badgeStatus]').contains('Status: Live').should('exist');
        cy.get('[data-cy=goLive]').should('not.exist');
    });

    it('new badge button should retain focus after dialog is closed', () => {
        cy.intercept('GET', `/supervisor/badges`).as('getGlobalBadges');

        cy.visit('/');
        cy.clickNav('Badges');
        cy.wait('@getGlobalBadges');

        cy.get('[aria-label="new global badge"]').click();
        cy.get('[data-cy=closeBadgeButton]').click();
        cy.get('[aria-label="new global badge"]').should('have.focus');

        cy.get('[aria-label="new global badge"]').click();
        cy.get('[data-cy=badgeName]').type('{esc}');
        cy.get('[aria-label="new global badge"]').should('have.focus');

        cy.get('[aria-label="new global badge"]').click();
        cy.get('[aria-label=Close]').filter('.text-light').click();
        cy.get('[aria-label="new global badge"]').should('have.focus');

        cy.get('[aria-label="new global badge"]').click();
        cy.get('[data-cy=badgeName]').type('test 123');
        cy.get('[data-cy=saveBadgeButton]').click();
        cy.get('[aria-label="new global badge"]').should('have.focus');
    });

    it('edit badge button should retain focus after dialog is closed', () => {
        cy.request('POST', '/supervisor/badges/badge1', {
            projectId: 'proj1',
            badgeId: 'badge1',
            name: "Badge 1"
        });

        cy.request('POST', '/supervisor/badges/badge2', {
            projectId: 'proj1',
            badgeId: 'badge2',
            name: "Badge 2"
        });

        cy.intercept('GET', `/supervisor/badges`).as('getGlobalBadges');

        cy.visit('/');
        cy.clickNav('Badges');
        cy.wait('@getGlobalBadges');

        cy.get('div.badge-settings').eq(0).click();
        cy.get('[data-cy=editMenuEditBtn]').eq(0).click();
        cy.get('[data-cy=closeBadgeButton]').click();
        cy.get('div.badge-settings').eq(0).children().first().should('have.focus');

        cy.get('div.badge-settings').eq(0).click();
        cy.get('[data-cy=editMenuEditBtn]').eq(0).click();
        cy.get('[aria-label=Close]').filter('.text-light').click();
        cy.get('div.badge-settings').eq(0).children().first().should('have.focus');

        cy.get('div.badge-settings').eq(0).click();
        cy.get('[data-cy=editMenuEditBtn]').eq(0).click();
        cy.get('[data-cy=badgeName]').type('{esc}');
        cy.get('div.badge-settings').eq(0).children().first().should('have.focus');


        cy.get('div.badge-settings').eq(1).click();
        cy.get('[data-cy=editMenuEditBtn]').eq(1).click();
        cy.get('[data-cy=closeBadgeButton]').click();
        cy.get('div.badge-settings').eq(1).children().first().should('have.focus');

        cy.get('div.badge-settings').eq(1).click();
        cy.get('[data-cy=editMenuEditBtn]').eq(1).click();
        cy.get('[aria-label=Close]').filter('.text-light').click();
        cy.get('div.badge-settings').eq(1).children().first().should('have.focus');

        cy.get('div.badge-settings').eq(1).click();
        cy.get('[data-cy=editMenuEditBtn]').eq(1).click();
        cy.get('[data-cy=badgeName]').type('{esc}');
        cy.get('div.badge-settings').eq(1).children().first().should('have.focus');
    });

});
