package skills.intTests


import org.joda.time.DateTime
import skills.intTests.utils.SkillsService

class PopulateGeoAnalyzerProjForDemo {

    String projectId = 'GeoSpatialAnalyzer'
    int numPerformToCompletion = 20
    int pointIncrement = 10
    def users = ['oscar.higgins', 'charlotte.richardson', 'lucia.morris', 'byron.walker', 'hailey.scott', 'garry.cunningham', 'kelvin.hawkins', 'andrew.walker', 'chris.moses', 'arnold.scott', 'evelyn.evans', 'blake.cooper', 'henry.baker', 'maddie.davis', 'amber.cunningham', 'michelle.watson', 'oliver.payne', 'amber.lloyd', 'dima.may', 'edward.casey', 'ryan.mayo', 'florrie.tucker', 'kevin.riley']
    def project = [projectId: projectId, name: 'Geo Spatial Analyzer']

    Map<String, List<String>> subjectsAndSkills = [
            'Measurement'        : ['Intersection - Geo JSON', 'Population Growth', 'Area Calculation'],
            'Coordinate Mutation': ['Clean Coordinates', 'Flip', 'Rewind', 'Round', 'Truncate'],
            'Transformation'     : ['Bounding Box Clip', 'Bezier Spline', 'Clone', 'Transform Rotate', 'Transform Translate', 'Transform Scale', 'Union'],
            'Feature Conversion' : ['Combine', 'Explode', 'Flatten', 'Line to Polygon', 'Polygon to Line'],
            'Interpolation'      : ['Interpolate', 'Isobands', 'Isolines'],
    ]

    Map<String, List<String>> badgesAndSkills = [
            'Map Detective'     : ['Intersection - Geo JSON', 'Bounding Box Clip', 'Isobands', 'Area Calculation', 'Transform Scale'],
            'Coordinate Wizard' : ['Clean Coordinates', 'Bezier Spline', 'Flip', 'Isobands', 'Isolines', 'Transform Rotate'],
    ]

    static void main(String[] args) {
        PopulateGeoAnalyzerProjForDemo driver = new PopulateGeoAnalyzerProjForDemo()

//        driver.test()

        driver.createService()
//        driver.populateSchema()
        driver.addSkills()
    }

    SkillsService skillsService

    void test() {
    }

    void populateSchema() {
        skillsService.createProject(project)
        subjectsAndSkills.each { String subjectName, List<String> skillNames ->
            {
                skillsService.createSubject(createSubject(subjectName))
                skillNames.each { skillName ->
                    {
                        skillsService.createSkill(createSkill(subjectName, skillName))
                    }
                }
            }
        }

        badgesAndSkills.each { String badgeName, List<String> skillNames -> {
            skillsService.createBadge(createBadge(badgeName))
            skillNames.each { skillName ->
                {
                    skillsService.assignSkillToBadge([projectId: projectId, badgeId: getBadgeId(badgeName), skillId: getSkillId(skillName)])
                }
            }
        }}
    }

    void addSkills() {
        List<String> skillIds = subjectsAndSkills.collect { subject, skillNames ->
            skillNames
        }.flatten().collect { getSkillId(it) }
        Integer numSkills = skillIds.size()
        Integer numSkillsAchievable = numSkills * numPerformToCompletion
        println skillIds
        users.each { userId -> {
            Random r = new Random();
            Integer randomNum = r.nextInt(100);
            Double percent = randomNum / 100
            Integer numSkillsToPerform = numSkillsAchievable * percent

            println "Adding [$numSkillsToPerform] skills (${percent * 100}%) for [$userId]"

            Map<String, Integer> skillsAdded = [:]
            for (int i = 0; i < numSkillsToPerform; i++) {
                Integer skillIdx = r.nextInt(numSkills);
                String skillId = skillIds[skillIdx]
                Integer daysAgo = r.nextInt(6*30)
                skillsService.addSkill([projectId: projectId, skillId: skillId], userId, new DateTime().minusDays(daysAgo).toDate())
                if (skillsAdded.containsKey(skillId)) {
                    Integer alreadyPerformed = skillsAdded[skillId]
                    skillsAdded[skillId] = alreadyPerformed + 1
                } else {
                    skillsAdded[skillId] = 1
                }
            }

            println "Added [$numSkillsToPerform] skills (${percent * 100}%) for [$userId]: \n ${skillsAdded}"
            println "done."
        }}
    }

    void createService(
            String username = 'skilltree@softtech-solutions.com',
            String password = "password",
            String firstName = "SkillTree",
            String lastName = "Demo",
            String url = 'https://ec2-3-15-227-169.us-east-2.compute.amazonaws.com/') {
        skillsService = new SkillsService(username, password, firstName, lastName, url, null)
    }

    Map createSubject(String subjectName) {
        return [projectId: projectId, subjectId: getSubjectId(subjectName), name: subjectName]
    }

    Map createBadge(String badgeName) {
        return [projectId: projectId, badgeId: getBadgeId(badgeName), name: badgeName, enabled: 'true']
    }

    String getSubjectId(String name) {
        return "${name.replaceAll("[^a-zA-Z0-9_]", "")}Subject"
    }

    String getSkillId(String name) {
        return "${name.replaceAll("[^a-zA-Z0-9_]", "")}Skill"
    }

    String getBadgeId(String name) {
        return "${name.replaceAll("[^a-zA-Z0-9_]", "")}Badge"
    }


    Map createSkill(String subjectId, String skillName, int version = 0, int pointIncrementInterval = 480) {
        return [projectId             : projectId, subjectId: getSubjectId(subjectId),
                skillId               : getSkillId(skillName),
                name                  : skillName,
                type                  : "Skill", pointIncrement: pointIncrement, numPerformToCompletion: numPerformToCompletion,
                pointIncrementInterval: pointIncrementInterval, numMaxOccurrencesIncrementInterval: 1,
                description           : "This skill [${skillName}] belongs to project [${projectId}]".toString(),
                helpUrl               : "http://veryhelpfulwebsite-${getSkillId(skillName)}".toString(),
                version               : version]
    }

    List<Map> createSkills(int numSkills, String subjId, String projId = projectId, int pointIncrement = 10) {
        return (1..numSkills).collect { createSkill(projId, subjId, it, 0, 1, 480, pointIncrement) }
    }

}
