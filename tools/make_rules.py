"""Writes database.rules.json (Firebase Realtime Database security rules).

Run from the repository root:  python3 tools/make_rules.py
Then paste database.rules.json into the Firebase console (Realtime Database -> Rules) and publish.

Who is who:
  teacher of a class   classes/{cid}/teachers/{uid} exists
  student tablet       classes/{cid}/devices/{uid} exists
  school admin         schools/{sid}/admins/{uid} exists; has teacher rights in every class
                       whose info/school is {sid}
"""
import json
import os

AUTH = "auth != null"


def cls(cid):
    return f"root.child('classes').child({cid})"


def new_cls(cid, up):
    """The class node in the data after the write, `up` levels above the current rule."""
    return "newData" + ".parent()" * up + f".child('classes').child({cid})"


def is_teacher(cid):
    return f"{cls(cid)}.child('teachers').child(auth.uid).exists()"


def is_school_admin(sid):
    return f"root.child('schools').child({sid}).child('admins').child(auth.uid).exists()"


def is_class_admin(cid):
    school = f"{cls(cid)}.child('info').child('school')"
    return f"({school}.exists() && {is_school_admin(school + '.val()')})"


def can_teach(cid):
    """Teacher of the class, or admin of the class's school."""
    return f"({is_teacher(cid)} || {is_class_admin(cid)})"


def is_device(cid):
    return f"{cls(cid)}.child('devices').child(auth.uid).exists()"


def all_of(*parts):
    return " && ".join(parts)


def any_of(*parts):
    return "(" + " || ".join(parts) + ")"


T = can_teach("$cid")
D = is_device("$cid")
MEMBER = any_of(T, D)

MESSAGE = all_of(
    "newData.hasChildren(['from', 'content', 'ts', 'by'])",
    "newData.child('content').isString()",
    "newData.child('content').val().length > 0",
    "newData.child('content').val().length <= 2000",
    "newData.child('from').isString()",
    "newData.child('from').val().length <= 40",
    "newData.child('ts').isNumber()",
    "newData.child('by').val() === auth.uid",
)


def name_ok(field="name", max_len=40):
    return all_of(
        f"newData.child('{field}').isString()",
        f"newData.child('{field}').val().length > 0",
        f"newData.child('{field}').val().length <= {max_len}",
    )


CODE = "$code.matches(/^[A-Z0-9]{6,10}$/)"

# ---------------------------------------------------------------- join codes
# {classId, role: student|teacher} or {schoolId, role: admin|school}
code_cid_old = "data.child('classId').val()"
code_cid_new = "newData.child('classId').val()"
code_sid_old = "data.child('schoolId').val()"
code_sid_new = "newData.child('schoolId').val()"

join_codes = {
    "$code": {
        ".read": AUTH,
        ".write": all_of(AUTH, any_of(
            # delete an old code
            all_of("data.exists()", "!newData.exists()", any_of(
                all_of("data.child('classId').exists()", can_teach(code_cid_old)),
                all_of("data.child('schoolId').exists()", is_school_admin(code_sid_old)),
            )),
            # add a new code: for an existing class/school you run, or one you are creating in the same write
            all_of("!data.exists()", "newData.exists()", any_of(
                all_of("newData.child('classId').exists()", any_of(
                    can_teach(code_cid_new),
                    all_of(f"!{cls(code_cid_new)}.exists()",
                           f"{new_cls(code_cid_new, 2)}.child('info').child('createdBy').val() === auth.uid"),
                )),
                all_of("newData.child('schoolId').exists()", any_of(
                    is_school_admin(code_sid_new),
                    all_of(f"!root.child('schools').child({code_sid_new}).exists()",
                           f"newData.parent().parent().child('schools').child({code_sid_new}).child('info').child('createdBy').val() === auth.uid"),
                )),
            )),
        )),
        ".validate": all_of(
            "newData.hasChild('role')",
            any_of(
                all_of("newData.child('classId').isString()",
                       "(newData.child('role').val() === 'student' || newData.child('role').val() === 'teacher')"),
                all_of("newData.child('schoolId').isString()",
                       "(newData.child('role').val() === 'admin' || newData.child('role').val() === 'school')"),
            ),
            CODE,
        ),
    },
}

# ---------------------------------------------------------------- class secrets
class_secrets = {
    "$cid": {
        ".read": all_of(AUTH, T),
        ".write": all_of(AUTH, "newData.exists()", any_of(
            T,
            all_of("!data.exists()", f"!{cls('$cid')}.exists()",
                   f"{new_cls('$cid', 2)}.child('info').child('createdBy').val() === auth.uid"),
        )),
        ".validate": all_of("newData.hasChildren(['studentCode', 'teacherCode'])",
                            "newData.child('studentCode').isString()", "newData.child('teacherCode').isString()"),
    },
}

# ---------------------------------------------------------------- classes
self_write = all_of(AUTH, "auth.uid === $uid", "data.parent().exists()")
new_school = "newData.child('school')"

classes = {
    "$cid": {
        "info": {
            ".read": all_of(AUTH, MEMBER),
            ".write": all_of(AUTH, "newData.exists()", any_of(
                all_of("!data.exists()", "newData.child('createdBy').val() === auth.uid"),
                T,
            )),
            ".validate": all_of(
                "newData.hasChildren(['name', 'createdBy'])",
                name_ok(),
                any_of(
                    # the school never changes
                    all_of("data.exists()", f"{new_school}.val() === data.child('school').val()"),
                    # a new class joins a school only together with its entry in the school's class list
                    # (which checks the school code)
                    all_of("!data.exists()", any_of(
                        f"!{new_school}.exists()",
                        all_of(f"{new_school}.isString()",
                               f"newData.parent().parent().parent().child('schools').child({new_school}.val())"
                               ".child('classes').child($cid).exists()"),
                    )),
                ),
            ),
        },
        "teachers": {
            ".read": all_of(AUTH, T),
            "$uid": {
                ".read": all_of(AUTH, "auth.uid === $uid"),
                ".write": all_of(AUTH, any_of(
                    all_of("auth.uid === $uid", "!data.exists()", "newData.exists()", any_of(
                        "newData.child('code').val() === root.child('classSecrets').child($cid).child('teacherCode').val()",
                        all_of(f"!{cls('$cid')}.child('info').exists()",
                               "newData.parent().parent().child('info').child('createdBy').val() === auth.uid"),
                    )),
                    all_of("!newData.exists()", T),
                )),
                ".validate": all_of("newData.hasChildren(['name', 'code', 'joinedAt'])", name_ok()),
            },
        },
        "devices": {
            ".read": all_of(AUTH, T),
            "$uid": {
                ".read": all_of(AUTH, "auth.uid === $uid"),
                ".write": all_of(AUTH, any_of(
                    all_of("auth.uid === $uid", "!data.exists()", "newData.exists()",
                           "newData.child('code').val() === root.child('classSecrets').child($cid).child('studentCode').val()"),
                    all_of("!newData.exists()", any_of("auth.uid === $uid", T)),
                )),
                ".validate": all_of("newData.hasChildren(['name', 'code', 'joinedAt'])", name_ok()),
                "name": {
                    ".write": all_of(AUTH, "data.parent().exists()", "newData.exists()", T),
                    ".validate": "newData.isString() && newData.val().length > 0 && newData.val().length <= 40",
                },
                "code": {".validate": "newData.isString()"},
                "joinedAt": {".validate": "newData.isNumber()"},
                "online": {".write": self_write, ".validate": "newData.isBoolean()"},
                "lastSeen": {".write": self_write, ".validate": "newData.isNumber()"},
                "battery": {".write": self_write,
                            ".validate": "newData.isNumber() && newData.val() >= 0 && newData.val() <= 100"},
                "charging": {".write": self_write, ".validate": "newData.isBoolean()"},
                "canLock": {".write": self_write, ".validate": "newData.isBoolean()"},
                "$other": {".validate": False},
            },
        },
        "sound": {
            ".read": all_of(AUTH, T),
            ".write": all_of(AUTH, T),
            "all": {".read": all_of(AUTH, MEMBER), ".validate": "newData.isBoolean()"},
            "until": {".read": all_of(AUTH, MEMBER), ".validate": "newData.isNumber()"},
            "devices": {
                "$uid": {".read": all_of(AUTH, "auth.uid === $uid"), ".validate": "newData.isBoolean()"},
            },
            "$other": {".validate": False},
        },
        "messages": {
            ".read": all_of(AUTH, MEMBER),
            "$mid": {
                ".write": all_of(AUTH, T, "(!data.exists() || !newData.exists())"),
                ".validate": MESSAGE,
            },
        },
        "inbox": {
            ".read": all_of(AUTH, T),
            "$uid": {
                ".read": all_of(AUTH, "auth.uid === $uid",
                                "root.child('classes').child($cid).child('devices').child($uid).exists()"),
                ".write": all_of(AUTH, "!newData.exists()", T),
                "$mid": {
                    ".write": all_of(AUTH, T, "(!data.exists() || !newData.exists())"),
                    ".validate": all_of(MESSAGE, "root.child('classes').child($cid).child('devices').child($uid).exists()"),
                },
            },
        },
        "opened": {
            ".read": all_of(AUTH, T),
            "$mid": {
                ".write": all_of(AUTH, "!newData.exists()", T),
                "$uid": {
                    ".write": all_of(
                        AUTH, "auth.uid === $uid", "!data.exists()", "newData.exists()",
                        "root.child('classes').child($cid).child('devices').child($uid).exists()",
                        any_of("root.child('classes').child($cid).child('messages').child($mid).exists()",
                               "root.child('classes').child($cid).child('inbox').child($uid).child($mid).exists()"),
                    ),
                    ".validate": "newData.isNumber()",
                },
            },
        },
        "attention": {
            ".read": all_of(AUTH, MEMBER),
            ".write": all_of(AUTH, T),
            ".validate": all_of("newData.hasChildren(['text', 'from', 'ts'])", name_ok("text", 100),
                                "newData.child('from').isString()", "newData.child('from').val().length <= 40",
                                "newData.child('ts').isNumber()"),
        },
    },
}

# ---------------------------------------------------------------- schools
SA = is_school_admin("$sid")
creating_school = all_of(
    "!root.child('schools').child($sid).child('info').exists()",
)

schools = {
    "$sid": {
        "info": {
            ".read": all_of(AUTH, SA),
            ".write": all_of(AUTH, "newData.exists()", any_of(
                all_of("!data.exists()", "newData.child('createdBy').val() === auth.uid"),
                SA,
            )),
            ".validate": all_of("newData.hasChildren(['name', 'createdBy'])", name_ok(max_len=60)),
        },
        "admins": {
            ".read": all_of(AUTH, SA),
            "$uid": {
                ".read": all_of(AUTH, "auth.uid === $uid"),
                ".write": all_of(AUTH, any_of(
                    all_of("auth.uid === $uid", "!data.exists()", "newData.exists()", any_of(
                        "newData.child('code').val() === root.child('schoolSecrets').child($sid).child('adminCode').val()",
                        all_of(creating_school,
                               "newData.parent().parent().child('info').child('createdBy').val() === auth.uid"),
                    )),
                    all_of("!newData.exists()", SA),
                )),
                ".validate": all_of("newData.hasChildren(['name', 'code', 'joinedAt'])", name_ok()),
            },
        },
        "classes": {
            ".read": all_of(AUTH, SA),
            "$cid": {
                # An admin, or a teacher creating a class in this school (with the school code, checked below)
                ".write": all_of(AUTH, any_of(
                    SA,
                    all_of("!data.exists()", "newData.exists()", f"!{cls('$cid')}.exists()",
                           f"{new_cls('$cid', 4)}.child('info').child('school').val() === $sid",
                           f"{new_cls('$cid', 4)}.child('info').child('createdBy').val() === auth.uid"),
                )),
                ".validate": all_of(
                    "newData.hasChild('name')", name_ok(),
                    any_of(SA, "newData.child('code').val() === root.child('schoolSecrets').child($sid).child('teacherCode').val()"),
                ),
            },
        },
    },
}

school_secrets = {
    "$sid": {
        ".read": all_of(AUTH, SA),
        ".write": all_of(AUTH, "newData.exists()", any_of(
            SA,
            all_of("!data.exists()", "!root.child('schools').child($sid).exists()",
                   "newData.parent().parent().child('schools').child($sid).child('info').child('createdBy').val() === auth.uid"),
        )),
        ".validate": all_of("newData.hasChildren(['adminCode', 'teacherCode'])",
                            "newData.child('adminCode').isString()", "newData.child('teacherCode').isString()"),
    },
}

rules = {
    "rules": {
        ".read": False,
        ".write": False,
        "joinCodes": join_codes,
        "classSecrets": class_secrets,
        "classes": classes,
        "schoolSecrets": school_secrets,
        "schools": schools,
    },
}

out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "database.rules.json")
with open(out, "w") as f:
    json.dump(rules, f, indent=2)
    f.write("\n")
print("wrote", os.path.normpath(out))
