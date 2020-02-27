##/keyword
##/when
##/then
##/usage
##/steps
#/result

[when]There (is( an?)?|are) historical {entityType}s?( that)? = ${entityType}: {entityType}() from $history['associated']
[when]There (is( an?)?|are) {entityType}s?( that)? = ${entityType}: {entityType}()
[when]Within {rule} history lookup results for {type} = $history: HistoryLookupResult(rule == '{rule}', $original: originalEntity as {type}) $eligible: ArrayList() from collect(
[when]End Collect = )
[when]first non null\s*\({tail}?= RuleUtils.firstNonNull({tail}
[when]sum non empty\s*\({tail}?= RuleUtils.sum({tail}
[when]round\s*\({tail}?= RuleUtils.round({tail}
[when]date\s*\({tail}?= RuleUtils.date({tail}
[when](is )?not within\s*\({tail}?= not in ({tail}
[when](is )?within\s*\({tail}?= in ({tail}
[when](is )?not one of\s*\({tail}?= not in ({tail}
[when](is )?one of\s*\({tail}?= in ({tail}
[when]ignore case '{tail}?= '(?i){tail}
[when]{param:[$\w\.!'\[\]]+} (is\s+)?not empty= !RuleUtils.isEmpty({param})
[when]{param:[$\w\.!'\[\]]+} (is\s+)?empty= RuleUtils.isEmpty({param})
[when]{param:[$\w\.!'\[\]]+} (is\s+)?not blank= !RuleUtils.isBlank({param})
[when]{param:[$\w\.!'\[\]]+} (is\s+)?blank= RuleUtils.isBlank({param})
[when]{param:[$\w\.!'\[\]]+} is {amount} days? after {param2:[$\w\.!'\[\]]+}= (RuleUtils.daysBetween({param2}, {param}) != null && >= {amount})
[when]{param:[$\w\.!'\[\]]+} is {amount} years? after {param2:[$\w\.!'\[\]]+}= (RuleUtils.yearsBetween({param2}, {param}) != null && >= {amount})
[when]{param:[$\w\.!'\[\]]+} contains? all of {param2:[$\w\.!'\[\]]+}= RuleUtils.containsAll({param}, {param2})
[when]{param:[$\w\.!'\[\]]+} contains? all of\s*\({tail}?= RuleUtils.containsAll({param}, {tail}
[when]{param:[$\w\.!'\[\]]+} (do(es)?\s+)?not contains? {param2:[$\w\.!]+}= !RuleUtils.contains({param}, {param2})
[when]{param:[$\w\.!'\[\]]+} (do(es)?\s+)?not contains?\s*\({tail}?= !RuleUtils.contains({param}, {tail}
[when]{param:[$\w\.!'\[\]]+} contains? {param2:[$\w\.!]+}= RuleUtils.contains({param}, {param2})
[when]{param:[$\w\.!'\[\]]+} contains?\s*\({tail}?= RuleUtils.contains({param}, {tail}
[when]{param:[$\w\.!'\[\]]+} (do(es)?\s+)?not match(es)? {param2:[$\w\.!]+}= !RuleUtils.matches({param}, {param2})
[when]{param:[$\w\.!'\[\]]+} (do(es)?\s+)?not match(es)?\s*\({tail}?= !RuleUtils.matches({param}, {tail}
[when]{param:[$\w\.!'\[\]]+} match(es)? {param2:[$\w\.!]+}= RuleUtils.matches({param}, {param2})
[when]{param:[$\w\.!'\[\]]+} match(es)?\s*\({tail}?= RuleUtils.matches({param}, {tail}
[when]{param:[$\w\.!'\[\]]+} starts? with {param2:[$\w\.!'\[\]]+}= RuleUtils.startsWith({param}, {param2})
[when]{param:[$\w\.!'\[\]]+} starts? with\s*\({tail}?= RuleUtils.startsWith({param}, {tail}
[when]{param:[$\w\.!'\[\]]+} ends? with {param2:[$\w\.!'\[\]]+}= RuleUtils.endsWith({param}, {param2})
[when]{param:[$\w\.!'\[\]]+} ends? with\s*\({tail}?= RuleUtils.endsWith({param}, {tail}
[when]{param:[$\w\.!'\[\]]+} union {param2:[$\w\.!'\[\]]+}= RuleUtils.union({param}, {param2})
[when]{param:[$\w\.!'\[\]]+} pick out\s*\({tail}?=RuleUtils.decode({param}, {tail}
[when]{param:[$\w\.!'\[\]]+} as {param2:[$\w\.!'\[\]]+}=(({param2}) {param})
[when](is\s+)?not less than(\s+an?)? = >=
[when](is\s+)?less than(\s+an?)? = <
[when](is\s+)?not greater than(\s+an?)? = <=
[when](is\s+)?greater than(\s+an?)? = >
[when]((is|do(es)?)\s+)?not equals?(\s+to)? = !=
[when](is\s+)?equals?(\s+to)? = ==
[when]is not(\s+an?)? = !=
[when]is(\s+an?)? = ==
[when]like(\s+an?)? = matches
[when]{prefix}?\s*(?<![\w])and(?![\w])\s*{suffix}? = {prefix} && {suffix}
[when]{prefix}?\s*(?<![\w])or(?![\w])\s*{suffix}? = {prefix} || {suffix}
[when]{prefix}?\s*(?<![\w])not(?! (in|matches|contains|memberOf|soundslike|str))(\s+an?)?(?![\w])\s*\({suffix}? = {prefix} false == (true && {suffix}
[when]{prefix}?\s*(?<![\w])not(?! (in|matches|contains|memberOf|soundslike|str))(\s+an?)?(?![\w])\s*{suffix}? = {prefix} !{suffix}
[when](?<![^\(,])\s*- = 
